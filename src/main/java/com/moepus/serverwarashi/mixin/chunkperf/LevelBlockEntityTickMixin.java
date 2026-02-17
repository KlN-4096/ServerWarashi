package com.moepus.serverwarashi.mixin.chunkperf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.chunkperf.ChunkPerfManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 用于测量选定区块组方块实体 tick 耗时的 Mixin。
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelBlockEntityTickMixin {
    /**
     * 指示该世界是否为客户端侧。
     */
    @Final
    @Shadow
    public boolean isClientSide;

    /**
     * 包装方块实体 tick，在启用跟踪时记录耗时。
     *
     * @param tickingBlockEntity 正在 tick 的方块实体
     * @param original           原始 tick 调用
     */
    @WrapOperation(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V")
    )
    private void onTickBlockEntity(TickingBlockEntity tickingBlockEntity, Operation<Void> original) {
        if (this.isClientSide) {
            original.call(tickingBlockEntity);
            return;
        }

        Level level = (Level) (Object) this;
        if (tickingBlockEntity.getPos() == null) {
            original.call(tickingBlockEntity);
            return;
        }
        long sessionId = ChunkPerfManager.resolveTrackSessionId(level, tickingBlockEntity.getPos());
        if (sessionId < 0L) {
            original.call(tickingBlockEntity);
            return;
        }

        long start = System.nanoTime();
        original.call(tickingBlockEntity);
        long duration = System.nanoTime() - start;
        ChunkPerfManager.onEntityTick(level, tickingBlockEntity.getPos(), tickingBlockEntity.getType(), duration, true, sessionId);
    }
}
