package com.moepus.serverwarashi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.chunkperf.ChunkPerfManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Mixin to measure entity tick durations for selected chunk groups.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelEntityTickMixin {
    /**
     * Indicates whether this level is client-side.
     */
    @Shadow
    public boolean isClientSide;

    /**
     * Wraps the entity tick to record duration when tracking is enabled.
     *
     * @param consumer the original consumer
     * @param entity   the entity being ticked
     * @param original the original call
     * @param <T>      entity type
     */
    @WrapOperation(
            method = "guardEntityTick",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V")
    )
    private <T> void onEntityTick(Consumer<T> consumer, T entity, Operation<Void> original) {
        if (this.isClientSide || !(entity instanceof Entity mcEntity)) {
            original.call(consumer, entity);
            return;
        }

        Level level = (Level) (Object) this;
        if (!ChunkPerfManager.shouldTrack(level, mcEntity.blockPosition())) {
            original.call(consumer, entity);
            return;
        }

        long start = System.nanoTime();
        original.call(consumer, entity);
        long duration = System.nanoTime() - start;
        ChunkPerfManager.onEntityTick(level, mcEntity.blockPosition(), mcEntity.getType().toString(), duration);
    }
}
