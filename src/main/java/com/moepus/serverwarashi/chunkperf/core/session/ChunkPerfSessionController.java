package com.moepus.serverwarashi.chunkperf.core.session;

import com.moepus.serverwarashi.chunkperf.core.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 性能会话入口：负责调度单分组会话与全分组会话。
 */
public final class ChunkPerfSessionController {
    private final ChunkPerfSessionTrackerSingle singleTracker;
    private final ChunkPerfSessionTrackerAllGroup allGroupTracker;

    /**
     * 创建会话控制器。
     *
     * @param groupCache 分组缓存与解析器
     */
    public ChunkPerfSessionController(ChunkPerfGroupCache groupCache) {
        this.singleTracker = new ChunkPerfSessionTrackerSingle(groupCache);
        this.allGroupTracker = new ChunkPerfSessionTrackerAllGroup(groupCache);
    }

    /**
     * 启动单分组性能会话。
     */
    public Component start(ServerLevel sourceLevel,
                           int groupIndex,
                           int durationSec,
                           UUID playerId) {
        if (hasActiveAnalysis(sourceLevel)) {
            return ChunkPerfMessages.analysisAlreadyRunning();
        }
        return singleTracker.start(sourceLevel, groupIndex, durationSec, playerId);
    }

    /**
     * 停止单分组会话并生成报告。
     */
    public Component stop(ServerLevel level) {
        if (singleTracker.hasActiveSession(level)) {
            return singleTracker.stop(level);
        }
        if (allGroupTracker.hasActiveSession(level)) {
            return allGroupTracker.stopAll(level);
        }
        return ChunkPerfMessages.noActiveAnalysis();
    }

    /**
     * 启动全分组性能分析会话。
     */
    public Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        if (hasActiveAnalysis(level)) {
            return ChunkPerfMessages.analysisAlreadyRunning();
        }
        return allGroupTracker.startAll(level, durationSec, playerId);
    }

    /**
     * 推进会话并处理定时分析。
     */
    public void tickSessions(MinecraftServer server) {
        singleTracker.tickSessions(server);
        allGroupTracker.tickSessions(server);
    }

    /**
     * 判断是否需要跟踪该位置。
     */
    public boolean shouldTrack(Level level, BlockPos pos) {
        return singleTracker.shouldTrack(level, pos)
                || allGroupTracker.shouldTrack(level, pos);
    }

    /**
     * 记录实体 Tick 数据（方块实体也作为实体处理）。
     */
    public void onEntityTick(Level level, BlockPos pos, String type, long durationNanos, boolean isBlockEntity) {
        singleTracker.onEntityTick(level, pos, type, durationNanos, isBlockEntity);
        allGroupTracker.onEntityTick(level, pos, durationNanos, isBlockEntity);
    }

    /**
     * 记录区块 Tick 数据。
     */
    public void onChunkTick(Level level, ChunkPos pos, long durationNanos) {
        singleTracker.onChunkTick(level, pos, durationNanos);
        allGroupTracker.onChunkTick(level, pos, durationNanos);
    }

    private boolean hasActiveAnalysis(ServerLevel level) {
        return singleTracker.hasActiveSession(level)
                || allGroupTracker.hasActiveSession(level);
    }

    static void tickAutoAnalyses(Map<ResourceKey<Level>, AutoAnalysis> autoAnalyses,
                                 MinecraftServer server,
                                 Function<ServerLevel, Component> stopFn) {
        if (autoAnalyses.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (var entry : new ArrayList<>(autoAnalyses.entrySet())) {
            AutoAnalysis analysis = entry.getValue();
            if (now < analysis.endAtMillis) {
                continue;
            }
            ResourceKey<Level> dim = entry.getKey();
            ServerLevel level = server.getLevel(dim);
            if (level == null) {
                autoAnalyses.remove(dim);
                continue;
            }
            Component report = stopFn.apply(level);
            if (analysis.playerId != null) {
                var player = server.getPlayerList().getPlayer(analysis.playerId);
                if (player != null) {
                    player.sendSystemMessage(report);
                } else {
                    server.sendSystemMessage(report);
                }
            } else {
                server.sendSystemMessage(report);
            }
        }
    }

    record AutoAnalysis(long endAtMillis, UUID playerId) {
    }
}
