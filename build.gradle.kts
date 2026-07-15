plugins {
    idea
    alias(libs.plugins.loom)
}

group = "org.greamples"
version = project.property("mod_version") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("epmap") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}


repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    minecraft(libs.minecraft)
    "clientImplementation"(libs.fabric.loader)
    "clientImplementation"(libs.fabric.api)
    "clientImplementation"(libs.clojure)
    "clientImplementation"("maven.modrinth:xaeros-minimap:fabric-${libs.versions.minecraft.get()}-${libs.versions.xaero.minimap.get()}")

    // Bundle the Clojure runtime into the mod jar (Jar-in-Jar) so it is present at runtime.
    include(libs.clojure)
    include(libs.clojure.spec)
    include(libs.clojure.core.specs)
}

// --- Clojure AOT compilation -------------------------------------------------
// Fabric loads JVM classes, not .clj source, so the Clojure client code is
// ahead-of-time compiled to bytecode. Loom's remapJar then remaps this bytecode
// from named (mojmap) to intermediary mappings, same as the Java/mixin classes.
val clojureSrc = file("src/client/clojure")
val clojureOut = layout.buildDirectory.dir("classes/clojure/client")

val compileClojure by tasks.registering(JavaExec::class) {
    group = "build"
    description = "AOT-compiles the Clojure client namespaces."

    val clientSourceSet = sourceSets.getByName("client")
    dependsOn(tasks.named("compileClientJava"))

    inputs.dir(clojureSrc)
    outputs.dir(clojureOut)

    // clojureSrc for the .clj source, plus the full client compile classpath so
    // Fabric API / Minecraft / Xaero types resolve during AOT.
    classpath = files(clojureSrc) + clientSourceSet.compileClasspath

    mainClass.set("clojure.lang.Compile")
    // Compiling the entrypoint namespace transitively compiles everything it requires.
    args = listOf("org.greamples.epmap.client")

    doFirst {
        val out = clojureOut.get().asFile
        out.mkdirs()
        systemProperty("clojure.compile.path", out.absolutePath)
    }
}

// Register the AOT output as an extra output of the client source set so it is
// on the runtime/compile classpath, and pack it into the jar explicitly (the
// source-set output alone is not picked up by Loom's jar packaging).
sourceSets.named("client") {
    output.dir(mapOf("builtBy" to compileClojure), clojureOut)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", libs.versions.minecraft.get())
    inputs.property("loader_version", libs.versions.fabric.loader.get())
    inputs.property("xaerominimap", libs.versions.xaero.minimap.get())
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
            "xaerominimap" to libs.versions.xaero.minimap.get(),
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.jar {
    // Pack the AOT-compiled Clojure classes so they are present alongside the
    // Java/mixin classes and get remapped by Loom's remapJar.
    dependsOn(compileClojure)
    from(clojureOut)

    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
