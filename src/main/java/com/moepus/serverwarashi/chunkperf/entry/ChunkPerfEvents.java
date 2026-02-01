package com.moepus.serverwarashi.chunkperf.entry;

import com.moepus.serverwarashi.Serverwarashi;
import com.moepus.serverwarashi.chunkperf.core.ChunkPerfManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 性能分析相关事件钩子。
 */
@EventBusSubscriber(modid = Serverwarashi.MODID)
public final class ChunkPerfEvents {
    private ChunkPerfEvents() {
    }

    /**
     * 推进会话并处理定时自动报告。
     *
     * @param event 服务器 Tick 事件
     */
    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        ChunkPerfManager.onServerTick(event.getServer());
    }
}
