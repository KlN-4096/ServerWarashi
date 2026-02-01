package com.moepus.serverwarashi.mixin;

import com.moepus.serverwarashi.chunkperf.ChunkPerfManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to measure chunk tick durations for selected chunk groups.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelChunkTickMixin {
    /**
     * Start time for the current chunk tick.
     */
    @Unique
    private long serverWarashi$chunkTickStart;

    /**
     * Records the start time for chunk ticking.
     *
     * @param chunk the chunk being ticked
     * @param randomTickSpeed random tick speed
     * @param ci    callback info
     */
    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void onTickChunkStart(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        serverWarashi$chunkTickStart = System.nanoTime();
    }

    /**
     * Records the end time for chunk ticking and reports if tracking is enabled.
     *
     * @param chunk the chunk being ticked
     * @param randomTickSpeed random tick speed
     * @param ci    callback info
     */
    @Inject(method = "tickChunk", at = @At("TAIL"))
    private void onTickChunkEnd(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        long duration = System.nanoTime() - serverWarashi$chunkTickStart;
        ServerLevel level = (ServerLevel) (Object) this;
        ChunkPos pos = chunk.getPos();
        if (ChunkPerfManager.shouldTrack(level, pos.getWorldPosition())) {
            ChunkPerfManager.onChunkTick(level, pos, duration);
        }
    }
}
