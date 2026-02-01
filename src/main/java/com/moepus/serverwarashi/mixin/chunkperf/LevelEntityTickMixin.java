package com.moepus.serverwarashi.mixin.chunkperf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.chunkperf.core.ChunkPerfManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * 用于测量选定区块组实体 tick 耗时的 Mixin。
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelEntityTickMixin {
    /**
     * 指示该世界是否为客户端侧。
     */
    @Final
    @Shadow
    public boolean isClientSide;

    /**
     * 包装实体 tick，在启用跟踪时记录耗时。
     *
     * @param consumer 原始 consumer
     * @param entity   正在 tick 的实体
     * @param original 原始调用
     * @param <T>      实体类型
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
        ChunkPerfManager.onEntityTick(level, mcEntity.blockPosition(), mcEntity.getType().toString(), duration, false);
    }
}
