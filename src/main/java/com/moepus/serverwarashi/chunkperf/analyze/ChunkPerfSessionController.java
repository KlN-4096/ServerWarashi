package com.moepus.serverwarashi.chunkperf.analyze;

import com.moepus.serverwarashi.chunkperf.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupIndex;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupView;
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
    private final AnalyzeSingleGroup singleTracker;
    private final AnalyzeAllGroup allGroupTracker;
    private long nextSessionId = 1L;
    private long activeSessionId = -1L;
    private AbstractGroupAnalyzer<?> activeTracker;

    public ChunkPerfSessionController(ChunkPerfGroupView groupView,
                                      ChunkPerfGroupIndex groupIndex) {
        this.singleTracker = new AnalyzeSingleGroup(groupView);
        this.allGroupTracker = new AnalyzeAllGroup(groupView, groupIndex);
    }

    public Component start(ServerLevel sourceLevel,
                           int groupIndex,
                           int durationSec,
                           UUID playerId) {
        if (hasActiveSession()) {
            return ChunkPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        Component response = singleTracker.start(sourceLevel, groupIndex, durationSec, playerId, sessionId);
        activateTrackerIfStarted(singleTracker, sessionId);
        return response;
    }

    public Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        if (hasActiveSession()) {
            return ChunkPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        Component response = allGroupTracker.startAll(level, durationSec, playerId, sessionId);
        activateTrackerIfStarted(allGroupTracker, sessionId);
        return response;
    }

    public Component stop(ServerLevel level) {
        if (!hasActiveSession()) {
            return ChunkPerfMessages.noActiveAnalysis();
        }
        Component report = activeTracker.stopById(level.getServer(), activeSessionId);
        clearActiveSession();
        return report;
    }

    public void tickSessions(MinecraftServer server) {
        if (activeTracker == null) {
            return;
        }
        activeTracker.tickSessions(server);
        if (!activeTracker.hasAnyActiveSession()) {
            clearActiveSession();
        }
    }

    public long resolveTrackSessionId(Level level, BlockPos pos) {
        if (activeTracker == null) {
            return -1L;
        }
        return activeTracker.resolveTrackSessionId(level, pos);
    }

    public void onEntityTick(Level level,
                             BlockPos pos,
                             String type,
                             String sourceId,
                             String sourceLabel,
                             long durationNanos,
                             boolean isBlockEntity,
                             long sessionId) {
        if (activeTracker == null || sessionId < 0L || sessionId != activeSessionId) {
            return;
        }
        activeTracker.onEntityTickById(level, pos, type, sourceId, sourceLabel, durationNanos, isBlockEntity, sessionId);
    }

    public void onChunkTick(Level level, ChunkPos pos, long durationNanos, long sessionId) {
        if (activeTracker == null || sessionId < 0L || sessionId != activeSessionId) {
            return;
        }
        activeTracker.onChunkTickById(level, pos, durationNanos, sessionId);
    }

    private boolean hasActiveSession() {
        return activeTracker != null && activeSessionId >= 0L;
    }

    private void activateTrackerIfStarted(AbstractGroupAnalyzer<?> tracker, long sessionId) {
        if (!tracker.hasAnyActiveSession()) {
            return;
        }
        activeTracker = tracker;
        activeSessionId = sessionId;
    }

    private void clearActiveSession() {
        activeTracker = null;
        activeSessionId = -1L;
    }

    public static void tickAutoAnalyses(Map<ResourceKey<Level>, AutoAnalysis> autoAnalyses,
                                        MinecraftServer server,
                                        Function<ServerLevel, Component> stopFn) {
        if (autoAnalyses.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (var entry : new ArrayList<>(autoAnalyses.entrySet())) {
            AutoAnalysis analysis = entry.getValue();
            if (now < analysis.endAtNanos) {
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

    public record AutoAnalysis(long endAtNanos, UUID playerId) {
    }
}
