package org.greamples.epmap.client

import org.slf4j.LoggerFactory
import xaero.map.region.MapTile
import java.util.concurrent.atomic.AtomicLong

// пока просто читаем поля MapBlock и пишем в лог
object MapCaptureHook {
    private val LOGGER = LoggerFactory.getLogger("EPMap")
    private val tiles = AtomicLong()

    fun onTileWritten(tile: MapTile) {
        val n = tiles.incrementAndGet()

        if (n <= 5L || n % 100L == 0L) { // чтоб не спамить
            val b = tile.getBlock(0, 0)
            LOGGER.info("[EPMap] тайл #{} @ chunk({},{}) — (0,0): state={}, h={}, biome={}", n, tile.chunkX, tile.chunkZ, b?.state, b?.height, b?.biome)
        }
    }
}
