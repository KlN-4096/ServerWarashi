package com.moepus.serverwarashi.mixin.chunkperf;

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
 * 用于测量选定区块组区块 tick 耗时的 Mixin。
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelChunkTickMixin {
    /**
     * 当前区块 tick 的开始时间。
     */
    @Unique
    private long serverWarashi$chunkTickStart;

    /**
     * 记录区块 tick 的开始时间。
     *
     * @param chunk           正在 tick 的区块
     * @param randomTickSpeed 随机 tick 速度
     * @param ci              回调信息
     */
    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void onTickChunkStart(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        serverWarashi$chunkTickStart = System.nanoTime();
    }

    /**
     * 记录区块 tick 的结束时间，并在启用跟踪时上报。
     *
     * @param chunk           正在 tick 的区块
     * @param randomTickSpeed 随机 tick 速度
     * @param ci              回调信息
     */
    @Inject(method = "tickChunk", at = @At("TAIL"))
    private void onTickChunkEnd(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        long duration = System.nanoTime() - serverWarashi$chunkTickStart;
        ServerLevel level = (ServerLevel) (Object) this;
        ChunkPos pos = chunk.getPos();
        long sessionId = ChunkPerfManager.resolveTrackSessionId(level, pos.getWorldPosition());
        if (sessionId >= 0L) {
            ChunkPerfManager.onChunkTick(level, pos, duration, sessionId);
        }
    }
}
