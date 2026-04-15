package com.moepus.serverwarashi.mixin.chunkperf;

import com.moepus.serverwarashi.modules.performance.TicketPerfMixinHooks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.List;

/**
 * 在方块实体迭代器层做轻量包装，未分析时直接返回原始迭代器。
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelBlockEntityTickMixin {
    @Redirect(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;")
    )
    private Iterator<TickingBlockEntity> onBlockEntityIterator(List<TickingBlockEntity> tickers) {
        return TicketPerfMixinHooks.wrapBlockEntityIterator((Level) (Object) this, tickers.iterator());
    }
}
