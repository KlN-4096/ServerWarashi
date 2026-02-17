package com.moepus.serverwarashi.chunkperf.analyze;

import com.moepus.serverwarashi.TicketManager;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupView;
import com.moepus.serverwarashi.chunkperf.data.analyze.GroupSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

abstract class AbstractGroupAnalyzer<S extends GroupSession> {
    @FunctionalInterface
    protected interface SessionStarter<S extends GroupSession> {
        StartResult<S> prepare(int effectiveDuration);
    }

    protected record StartResult<S extends GroupSession>(S session, Component response, Component error) {
    }

    protected final ChunkPerfGroupView groupView;
    private final Map<ResourceKey<Level>, S> sessions = new HashMap<>();
    private final Map<ResourceKey<Level>, ChunkPerfSessionController.AutoAnalysis> autoAnalyses = new HashMap<>();

    protected AbstractGroupAnalyzer(ChunkPerfGroupView groupView) {
        this.groupView = groupView;
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
        int effectiveDuration = Math.max(durationSec, 1);
        TicketManager.pauseAutoBucketing(level, true);
        StartResult<S> result = starter.prepare(effectiveDuration);
        if (result.error() != null) {
            TicketManager.pauseAutoBucketing(level, false);
            return result.error();
        }
        if (result.session() == null || result.response() == null) {
            TicketManager.pauseAutoBucketing(level, false);
            throw new IllegalStateException("Session starter must return a session and response when no error is present");
        }
        sessions.put(level.dimension(), result.session());
        autoAnalyses.put(
                level.dimension(),
                new ChunkPerfSessionController.AutoAnalysis(
                        System.nanoTime() + effectiveDuration * 1_000_000_000L,
                        playerId
                )
        );
        return result.response();
    }

    protected final Component stopSession(ServerLevel level) {
        S session = sessions.remove(level.dimension());
        if (session == null) {
            return noActiveSessionMessage();
        }
        autoAnalyses.remove(level.dimension());
        TicketManager.pauseAutoBucketing(level, false);
        flushPending(session);
        return buildReport(session);
    }

    public final boolean hasAnyActiveSession() {
        return !sessions.isEmpty();
    }

    public final Component stopById(MinecraftServer server, long sessionId) {
        S session = sessions.values().stream().findFirst().orElse(null);
        if (session == null || session.id != sessionId) {
            return noActiveSessionMessage();
        }
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) {
            sessions.clear();
            autoAnalyses.clear();
            return noActiveSessionMessage();
        }
        return stopSession(level);
    }

    public final void tickSessions(MinecraftServer server) {
        for (var session : sessions.values()) {
            session.serverTickCount++;
        }
        ChunkPerfSessionController.tickAutoAnalyses(autoAnalyses, server, this::stopSession);
    }

    public final long resolveTrackSessionId(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return -1L;
        }
        S session = sessions.get(serverLevel.dimension());
        return session != null && isTrackedChunk(session, ChunkPos.asLong(pos)) ? session.id : -1L;
    }

    public final void onEntityTickById(Level level,
                                       BlockPos pos,
                                       String type,
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
        recordEntityTick(session, ChunkPos.asLong(pos), type, durationNanos, isBlockEntity);
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
        S session = sessions.get(serverLevel.dimension());
        if (session == null || session.id != sessionId) {
            return null;
        }
        return session;
    }

    protected abstract boolean isTrackedChunk(S session, long chunkPos);

    protected abstract void recordEntityTick(S session,
                                             long chunkPos,
                                             String type,
                                             long durationNanos,
                                             boolean isBlockEntity);

    protected abstract void recordChunkTick(S session, ChunkPos pos, long durationNanos);

    protected abstract void flushPending(S session);

    protected abstract Component buildReport(S session);

    protected abstract Component noActiveSessionMessage();
}
