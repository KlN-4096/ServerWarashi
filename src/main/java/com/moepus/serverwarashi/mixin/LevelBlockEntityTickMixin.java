package com.moepus.serverwarashi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.chunkperf.ChunkPerfManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to measure block entity tick durations for selected chunk groups.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelBlockEntityTickMixin {
    /**
     * Indicates whether this level is client-side.
     */
    @Shadow
    public boolean isClientSide;

    /**
     * Wraps the block entity tick to record duration when tracking is enabled.
     *
     * @param tickingBlockEntity the ticking block entity
     * @param original           the original tick call
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
        if (!ChunkPerfManager.shouldTrack(level, tickingBlockEntity.getPos())) {
            original.call(tickingBlockEntity);
            return;
        }

        long start = System.nanoTime();
        original.call(tickingBlockEntity);
        long duration = System.nanoTime() - start;
        ChunkPerfManager.onBlockEntityTick(level, tickingBlockEntity.getPos(), tickingBlockEntity.getType(), duration);
    }
}
