package com.moepus.serverwarashi.modules.idlefreeze;

import com.moepus.serverwarashi.config.IdleFreezeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * IdleFreeze 模块运行时协调入口。
 * 负责事件驱动状态和玩家访问跟踪，不承载冻结判定本体。
 */
public final class IdleFreezeRuntime {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private IdleFreezeRuntime() {
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!IdleFreezeConfig.enabled()) {
            return;
        }
        for (var level : server.getAllLevels()) {
            IdleFreezeService.reapplySavedIdlePause(level);
        }
        int delay = Math.max(IdleFreezeConfig.initialScanDelayTicks(), 0);
        server.tell(new TickTask(server.getTickCount() + delay, () -> {
            for (var level : server.getAllLevels()) {
                IdleFreezeService.runInitialScan(level);
            }
        }));
    }

    public static void onPlayerTickPost(ServerPlayer player) {
        if (!IdleFreezeConfig.enabled()) {
            return;
        }
        if (player.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        long chunkPos = player.chunkPosition().toLong();
        IdleVisitData.get(player.serverLevel()).markSeen(chunkPos, IdleFreezeService.currentEpochDay());
        IdleFreezeService.tryUnfreezeByChunk(player.serverLevel(), chunkPos);
    }
}
