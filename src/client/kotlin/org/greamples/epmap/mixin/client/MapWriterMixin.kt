package org.greamples.epmap.mixin.client

import com.llamalad7.mixinextras.sugar.Local
import org.greamples.epmap.client.MapCaptureHook
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import xaero.map.MapWriter
import xaero.map.region.MapTile

@Mixin(MapWriter::class)
class MapWriterMixin {

    @Inject(method = ["writeChunk"], at = [At(value = "INVOKE", target = "Lxaero/map/region/MapTile;setWrittenOnce(Z)V")])
    private fun epmap_onTileWritten(
        cir: CallbackInfoReturnable<Boolean>,
        @Local(ordinal = 0) mapTile: MapTile
    ) {
        MapCaptureHook.onTileWritten(mapTile)
    }
}
