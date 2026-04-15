package com.moepus.serverwarashi.modules.idlefreeze;

import com.moepus.serverwarashi.config.IdleFreezeConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IdleFreeze 模块运行时协调入口。
 * 负责事件驱动状态和玩家访问跟踪，不承载冻结判定本体。
 */
public final class IdleFreezeRuntime {
    private static final Map<UUID, PlayerChunkRef> lastChunkByPlayer = new HashMap<>();
    private static int startupScanDelayTicks = -1;

    private IdleFreezeRuntime() {
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!IdleFreezeConfig.enabled()) {
            startupScanDelayTicks = -1;
            return;
        }
        for (var level : server.getAllLevels()) {
            IdleFreezeService.reapplySavedIdlePause(level);
        }
        scheduleInitialScan();
    }

    public static void onServerTickPost(MinecraftServer server) {
        if (!shouldRunInitialScanOnTick()) {
            return;
        }
        for (var level : server.getAllLevels()) {
            IdleFreezeService.runInitialScan(level);
        }
    }

    public static void onPlayerTickPost(ServerPlayer player) {
        if (!IdleFreezeConfig.enabled()) {
            return;
        }
        trackPlayerVisit(player);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        lastChunkByPlayer.remove(player.getUUID());
    }

    public static void clearRuntimeState() {
        startupScanDelayTicks = -1;
        lastChunkByPlayer.clear();
    }

    private static void scheduleInitialScan() {
        startupScanDelayTicks = Math.max(IdleFreezeConfig.initialScanDelayTicks(), 0);
    }

    private static boolean shouldRunInitialScanOnTick() {
        if (startupScanDelayTicks < 0) {
            return false;
        }
        if (startupScanDelayTicks-- > 0) {
            return false;
        }
        startupScanDelayTicks = -1;
        return true;
    }

    private static void trackPlayerVisit(ServerPlayer player) {
        long chunkPos = player.chunkPosition().toLong();
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        UUID playerId = player.getUUID();
        PlayerChunkRef currentRef = new PlayerChunkRef(dimension, chunkPos);
        PlayerChunkRef previousRef = lastChunkByPlayer.get(playerId);
        if (currentRef.equals(previousRef)) {
            return;
        }
        lastChunkByPlayer.put(playerId, currentRef);
        IdleVisitData.get(player.serverLevel()).markSeen(chunkPos, IdleFreezeService.currentEpochDay());
        IdleFreezeService.tryUnfreezeByChunk(player.serverLevel(), chunkPos);
    }

    private record PlayerChunkRef(ResourceKey<Level> dimension, long chunkPos) {
    }
}
