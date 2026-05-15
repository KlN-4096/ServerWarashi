package com.moepus.serverwarashi.modules.performance.analyze;

import com.moepus.serverwarashi.common.group.ChunkGroupService;
import com.moepus.serverwarashi.modules.performance.report.TicketPerfMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Function;

/**
 * 性能会话入口：负责调度单分组会话与全分组会话。
 */
public final class TicketPerfSessionController {
    private AnalyzeAbstractGroup<?> activeTracker;
    private final AnalyzeSingleGroup singleTracker;
    private final AnalyzeAllGroup allGroupTracker;
    private long nextSessionId = 1L;
    private long activeSessionId = -1L;

    public TicketPerfSessionController(ChunkGroupService queryService) {
        this.singleTracker = new AnalyzeSingleGroup(queryService);
        this.allGroupTracker = new AnalyzeAllGroup(queryService);
    }

    public Component start(ServerLevel sourceLevel,
                           BlockPos pos,
                           int durationSec,
                           UUID playerId) {
        if (hasActiveSession()) {
            return TicketPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        Component response = singleTracker.start(sourceLevel, pos, durationSec, playerId, sessionId);
        activateTrackerIfStarted(singleTracker, sessionId);
        return response;
    }

    public Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        if (hasActiveSession()) {
            return TicketPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        Component response = allGroupTracker.startAll(level, durationSec, playerId, sessionId);
        activateTrackerIfStarted(allGroupTracker, sessionId);
        return response;
    }

    public Component stop(ServerLevel level) {
        if (!hasActiveSession()) {
            return TicketPerfMessages.noActiveAnalysis();
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

    public boolean hasActiveSession() {
        return activeTracker != null && activeSessionId >= 0L;
    }

    public boolean hasActiveSession(ResourceKey<Level> dimension) {
        return activeTracker != null
                && activeSessionId >= 0L
                && activeTracker.hasActiveSession(dimension);
    }

    public void clearRuntimeState() {
        singleTracker.clearRuntimeState();
        allGroupTracker.clearRuntimeState();
        activeTracker = null;
        activeSessionId = -1L;
        nextSessionId = 1L;
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

    private void activateTrackerIfStarted(AnalyzeAbstractGroup<?> tracker, long sessionId) {
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

    public static AutoAnalysis tickAutoAnalysis(AutoAnalysis autoAnalysis,
                                                ResourceKey<Level> dimension,
                                                MinecraftServer server,
                                                Function<ServerLevel, Component> stopFn) {
        if (autoAnalysis == null) {
            return null;
        }
        if (System.nanoTime() < autoAnalysis.endAtNanos()) {
            return autoAnalysis;
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return null;
        }
        Component report = stopFn.apply(level);
        var player = autoAnalysis.playerId() == null ? null : server.getPlayerList().getPlayer(autoAnalysis.playerId());
        if (player != null) {
            player.sendSystemMessage(report);
        } else {
            server.sendSystemMessage(report);
        }
        return null;
    }

    public record AutoAnalysis(long endAtNanos, UUID playerId) {
    }
}
