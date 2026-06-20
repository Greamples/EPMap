package org.greamples.epmap.client.xaero

/**
 * Читаемая обёртка над 32-битным полем `parametres`, которое Xaero's World Map
 * пишет в начале каждого пикселя (`MapBlock`) в файле `region.xaero`.
 *
 * Xaero пакует туда кучу мелких данных, чтобы не плодить отдельные поля на каждый
 * из 512×512 пикселей региона. Здесь вся эта битовая магия спрятана за понятными
 * именами — в самом ридере уже не нужно ни одного `>>` или `&` руками.
 *
 * Раскладка снята с декомпилированных `MapBlock.getParametres()` и
 * `MapSaveLoad.loadPixel()`. Целевой формат — major=7 / minor=8 (текущий).
 * Легаси-биты (2,3 — colourType; 4 — slope; 5,7 — не используются) в этой версии
 * всегда нули и здесь намеренно не разбираются.
 *
 * Карта битов:
 * ```
 *  0      есть блок-стейт (если 0 → пиксель это GRASS_BLOCK)
 *  1      есть оверлеи
 *  6      высота лежит отдельным байтом (запасной путь, см. hasSeparateHeightByte)
 *  8..11  уровень света (0..15)
 *  12..19 младшие 8 бит высоты
 *  20     есть биом
 *  21     блок-стейт встречается впервые → дальше NBT (иначе индекс в палитру)
 *  22     биом встречается впервые → дальше строка (иначе индекс в палитру)
 *  23     биом записан числом, а не строкой
 *  24     topHeight отличается от height → дальше отдельный байт
 *  25..28 старшие 4 бита высоты
 * ```
 */
@JvmInline
value class PixelFlags(val raw: Int) {

    // --- Наличие данных: «горит ли лампочка» ---

    /** Бит 0. Если false — у пикселя нет своего стейта, это GRASS_BLOCK (частый случай, его не пишут). */
    val hasBlockState: Boolean get() = raw and BIT_HAS_STATE != 0

    /** Бит 1. Есть ли оверлеи (вода/прозрачные блоки поверх). */
    val hasOverlays: Boolean get() = raw and BIT_HAS_OVERLAYS != 0

    /** Бит 20. Записан ли у пикселя биом. */
    val hasBiome: Boolean get() = raw and BIT_HAS_BIOME != 0

    // --- Как читать дальше из потока ---

    /** Бит 21. true → блок встречается впервые, дальше идёт полный NBT. false → дальше int-индекс в палитру региона. */
    val isBlockStateNew: Boolean get() = raw and BIT_STATE_NEW != 0

    /** Бит 22. true → биом впервые, дальше идёт его идентификатор. false → дальше int-индекс в палитру биомов. */
    val isBiomeNew: Boolean get() = raw and BIT_BIOME_NEW != 0

    /** Бит 23. Только когда [isBiomeNew]: true → биом как int, false → как UTF-строка. */
    val isBiomeStoredAsInt: Boolean get() = raw and BIT_BIOME_AS_INT != 0

    /** Бит 24. topHeight ≠ height → после высоты идёт отдельный байт с topHeight. Иначе topHeight = height. */
    val hasSeparateTopHeight: Boolean get() = raw and BIT_TOPHEIGHT_DIFFERS != 0

    /**
     * Бит 6. Запасной путь: высота записана отдельным байтом, а не упакована в [packedHeight].
     * Современный writer его не выставляет, но читатель обрабатывает на всякий случай.
     */
    val hasSeparateHeightByte: Boolean get() = raw and BIT_HEIGHT_AS_BYTE != 0

    // --- Упакованные числа ---

    /** Биты 8..11. Уровень света 0..15. */
    val light: Int get() = (raw ushr 8) and 0xF

    /**
     * Высота, собранная из двух разорванных кусков: младшие 8 бит (12..19) и старшие 4 бита (25..28).
     * Это 12-битное знаковое число (высота бывает отрицательной — пещеры, y < 0), поэтому в конце
     * восстанавливаем знак.
     *
     * Использовать только когда [hasSeparateHeightByte] == false; иначе высоту надо читать байтом из потока.
     */
    val packedHeight: Int
        get() {
            val low8 = (raw ushr 12) and 0xFF        // биты 12..19
            val high4 = (raw ushr 25) and 0xF        // биты 25..28
            val raw12 = (high4 shl 8) or low8        // склейка в 12-битное значение
            return (raw12 shl 20) shr 20             // знаковое расширение 12 бит → Int
        }

    companion object {
        private const val BIT_HAS_STATE = 0x1            // бит 0
        private const val BIT_HAS_OVERLAYS = 0x2         // бит 1
        private const val BIT_HEIGHT_AS_BYTE = 0x40      // бит 6
        private const val BIT_HAS_BIOME = 0x100000       // бит 20
        private const val BIT_STATE_NEW = 0x200000       // бит 21
        private const val BIT_BIOME_NEW = 0x400000       // бит 22
        private const val BIT_BIOME_AS_INT = 0x800000    // бит 23
        private const val BIT_TOPHEIGHT_DIFFERS = 0x1000000 // бит 24
    }
}
