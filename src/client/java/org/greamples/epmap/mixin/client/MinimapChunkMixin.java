package org.greamples.epmap.mixin.client;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.region.MinimapChunk;

import java.nio.IntBuffer;

// Shadows Xaero's Minimap internals (MinimapChunk#X/Z/buffer and #updateBuffers).
// These are Xaero-version-specific: pin the xaerominimap dependency and re-verify
// these fields/method whenever that version is bumped.
@Mixin(MinimapChunk.class)
public abstract class MinimapChunkMixin {

    // The mod logic lives in AOT-compiled Clojure. We bind to its vars lazily via
    // the stable clojure.java.api.Clojure API rather than referencing the generated
    // class directly, which keeps the Java compile step independent of the Clojure
    // compile step (no build-graph cycle). Clojure.var(...) triggers the require.
    private static final IFn IS_TARGET_SERVER;
    private static final IFn ADD_CHUNK;

    static {
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("org.greamples.epmap.client"));
        IS_TARGET_SERVER = Clojure.var("org.greamples.epmap.client", "target-server?");
        ADD_CHUNK = Clojure.var("org.greamples.epmap.client", "add-chunk");
    }

    @Shadow private int X;
    @Shadow private int Z;
    @Shadow private IntBuffer[] buffer;

    @Inject(method = "updateBuffers", at = @At("TAIL"))
    private void onRendered(int levelsToLoad, int[][] intArrayBuffer, CallbackInfo ci) {
        if (!(Boolean) IS_TARGET_SERVER.invoke()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimensionKey = minecraft.level.dimension();
        String dimensionId = dimensionKey.identifier().toString();
        int chunkX = this.X;
        int chunkZ = this.Z;
        if (this.buffer != null && this.buffer.length > 0) {
            IntBuffer topLevelBuffer = this.buffer[0];
            if (topLevelBuffer != null) {
                int[] pixelData = new int[topLevelBuffer.capacity()];
                IntBuffer copy = topLevelBuffer.duplicate();
                copy.rewind();
                copy.get(pixelData);
                // pixelData holds the chunk's ARGB colors
                ADD_CHUNK.invoke(dimensionId, chunkX, chunkZ, pixelData);
            }
        }
    }
}
