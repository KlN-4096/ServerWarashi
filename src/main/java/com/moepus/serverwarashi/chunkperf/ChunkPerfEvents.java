package com.moepus.serverwarashi.chunkperf;

import com.moepus.serverwarashi.Serverwarashi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 性能分析相关事件钩子。
 */
@EventBusSubscriber(modid = Serverwarashi.MODID)
public final class ChunkPerfEvents {
    private ChunkPerfEvents() {
    }

    /**
     * 服务器完全启动后构建快照与稳定索引。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ChunkPerfManager.rebuildSnapshots(event.getServer());
    }

    /**
     * 服务器停止时清理快照与稳定索引缓存。
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ChunkPerfManager.clearSnapshots();
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
