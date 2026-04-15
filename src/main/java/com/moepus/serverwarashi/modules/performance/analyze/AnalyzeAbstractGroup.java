package com.moepus.serverwarashi.modules.performance.analyze;

import com.moepus.serverwarashi.common.group.ChunkGroupService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

abstract class AnalyzeAbstractGroup<S extends GroupSession> {
    @FunctionalInterface
    protected interface SessionStarter<S extends GroupSession> {
        StartResult<S> prepare(int effectiveDuration);
    }

    protected record StartResult<S extends GroupSession>(S session, Component response, Component error) {
    }

    protected final ChunkGroupService queryService;
    private S activeSession;
    private TicketPerfSessionController.AutoAnalysis autoAnalysis;

    protected AnalyzeAbstractGroup(ChunkGroupService queryService) {
        this.queryService = queryService;
    }

    protected static <S extends GroupSession> StartResult<S> startSuccess(S session, Component response) {
        return new StartResult<>(session, response, null);
    }

    protected static <S extends GroupSession> StartResult<S> startError(Component error) {
        return new StartResult<>(null, null, error);
    }

    protected final Component startSession(ServerLevel level,
                                           int durationSec,
                                           UUID playerId,
                                           SessionStarter<S> starter) {
        if (activeSession != null) {
            throw new IllegalStateException("Only one active chunkperf session is supported");
        }
        int effectiveDuration = Math.max(durationSec, 1);
        StartResult<S> result = starter.prepare(effectiveDuration);
        if (result.error() != null) {
            return result.error();
        }
        if (result.session() == null || result.response() == null) {
            throw new IllegalStateException("Session starter must return a session and response when no error is present");
        }

        activeSession = result.session();
        autoAnalysis = new TicketPerfSessionController.AutoAnalysis(
                System.nanoTime() + effectiveDuration * 1_000_000_000L,
                playerId
        );
        return result.response();
    }

    protected final Component stopSession(ServerLevel level) {
        S session = activeSession;
        if (session == null || !session.dimension.equals(level.dimension())) {
            return noActiveSessionMessage();
        }

        activeSession = null;
        autoAnalysis = null;
        flushPending(session);
        return buildReport(session);
    }

    public final boolean hasAnyActiveSession() {
        return activeSession != null;
    }

    final boolean hasActiveSession(ResourceKey<Level> dimension) {
        S session = activeSession;
        return session != null && session.dimension.equals(dimension);
    }

    final void clearRuntimeState() {
        activeSession = null;
        autoAnalysis = null;
    }

    public final Component stopById(MinecraftServer server, long sessionId) {
        S session = activeSession;
        if (session == null || session.id != sessionId) {
            return noActiveSessionMessage();
        }
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) {
            activeSession = null;
            autoAnalysis = null;
            return noActiveSessionMessage();
        }
        return stopSession(level);
    }

    public final void tickSessions(MinecraftServer server) {
        S session = activeSession;
        if (session == null) {
            return;
        }
        session.serverTickCount++;
        autoAnalysis = TicketPerfSessionController.tickAutoAnalysis(
                autoAnalysis,
                session.dimension,
                server,
                this::stopSession
        );
    }

    public final long resolveTrackSessionId(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return -1L;
        }
        S session = activeSession;
        if (session == null || !session.dimension.equals(serverLevel.dimension())) {
            return -1L;
        }
        return isTrackedChunk(session, ChunkPos.asLong(pos)) ? session.id : -1L;
    }

    public final void onEntityTickById(Level level,
                                       BlockPos pos,
                                       String type,
                                       String sourceId,
                                       String sourceLabel,
                                       long durationNanos,
                                       boolean isBlockEntity,
                                       long sessionId) {
        if (pos == null) {
            return;
        }
        S session = resolveSessionById(level, sessionId);
        if (session == null) {
            return;
        }
        recordEntityTick(session, ChunkPos.asLong(pos), type, sourceId, sourceLabel, durationNanos, isBlockEntity);
    }

    public final void onChunkTickById(Level level, ChunkPos pos, long durationNanos, long sessionId) {
        S session = resolveSessionById(level, sessionId);
        if (session == null) {
            return;
        }
        recordChunkTick(session, pos, durationNanos);
    }

    private S resolveSessionById(Level level, long sessionId) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        S session = activeSession;
        if (session == null || !session.dimension.equals(serverLevel.dimension()) || session.id != sessionId) {
            return null;
        }
        return session;
    }

    protected abstract boolean isTrackedChunk(S session, long chunkPos);

    protected abstract void recordEntityTick(S session,
                                             long chunkPos,
                                             String type,
                                             String sourceId,
                                             String sourceLabel,
                                             long durationNanos,
                                             boolean isBlockEntity);

    protected abstract void recordChunkTick(S session, ChunkPos pos, long durationNanos);

    protected abstract void flushPending(S session);

    protected abstract Component buildReport(S session);

    protected abstract Component noActiveSessionMessage();
}

abstract class GroupSession {
    public final long id;
    public final ResourceKey<Level> dimension;
    public final long startedAtNanos;
    public long serverTickCount;

    protected GroupSession(long id, ResourceKey<Level> dimension) {
        this.id = id;
        this.dimension = dimension;
        this.startedAtNanos = System.nanoTime();
        this.serverTickCount = 0L;
    }
}
