package com.moepus.serverwarashi.mixin.chunkperf;

import com.moepus.serverwarashi.modules.performance.TicketPerfMixinHooks;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在区块 tick 调用点做 branch，避免在 ServerLevel.tickChunk 内部维持共享状态。
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ServerLevelChunkTickMixin {
    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"
            )
    )
    private void onTickChunk(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        TicketPerfMixinHooks.profileChunkTick(level, chunk, randomTickSpeed);
    }
}
