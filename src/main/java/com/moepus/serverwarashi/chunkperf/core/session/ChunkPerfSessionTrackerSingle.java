package com.moepus.serverwarashi.chunkperf.core.session;

import com.moepus.serverwarashi.chunkperf.core.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupCache;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupEntry;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import com.moepus.serverwarashi.utils.TicketPauseUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单分组性能会话跟踪器。
 */
final class ChunkPerfSessionTrackerSingle {
    private final ChunkPerfGroupCache groupCache;
    /**
     * 按维度维护的会话数据。
     */
    private final Map<ResourceKey<Level>, ChunkPerfSession> sessions = new HashMap<>();
    /**
     * 按维度维护的自动分析信息。
     */
    private final Map<ResourceKey<Level>, ChunkPerfSessionController.AutoAnalysis> autoAnalyses = new HashMap<>();

    ChunkPerfSessionTrackerSingle(ChunkPerfGroupCache groupCache) {
        this.groupCache = groupCache;
    }

    /**
     * 启动性能会话（按分组索引解析）。
     */
    Component start(ServerLevel sourceLevel,
                    int groupIndex,
                    int durationSec,
                    UUID playerId) {
        TicketPauseUtil.pauseAutoBucketing(sourceLevel, true);
        ChunkPerfGroupCache.GroupLookup lookup = groupCache.resolveGroup(
                sourceLevel,
                groupIndex,
                ChunkPerfTickets.PauseMode.ACTIVE_ONLY,
                ChunkPerfMessages.noTicketGroupsFound()
        );
        if (lookup.hasError()) {
            TicketPauseUtil.pauseAutoBucketing(sourceLevel, false);
            return lookup.error();
        }
        ChunkPerfGroupEntry entry = lookup.entry();
        LongOpenHashSet targetChunks = new LongOpenHashSet(entry.chunks());
        ChunkPerfSession session = new ChunkPerfSession(
                sourceLevel.dimension(),
                groupIndex,
                targetChunks,
                entry.label(),
                entry.blockEntityCount(),
                entry.entityCount()
        );
        sessions.put(sourceLevel.dimension(), session);
        if (durationSec > 0) {
            autoAnalyses.put(sourceLevel.dimension(),
                    new ChunkPerfSessionController.AutoAnalysis(System.currentTimeMillis() + durationSec * 1000L, playerId));
            return ChunkPerfMessages.sessionStarted(
                    entry.owner().asComponent(),
                    groupIndex,
                    entry.chunkCount(),
                    entry.blockEntityCount(),
                    entry.entityCount(),
                    durationSec
            );
        }
        autoAnalyses.remove(sourceLevel.dimension());
        return ChunkPerfMessages.sessionStarted(
                entry.owner().asComponent(),
                groupIndex,
                entry.chunkCount(),
                entry.blockEntityCount(),
                entry.entityCount(),
                0
        );
    }

    /**
     * 停止会话并生成报告。
     */
    Component stop(ServerLevel level) {
        ChunkPerfSession session = sessions.remove(level.dimension());
        if (session == null) {
            return ChunkPerfMessages.noActiveSession();
        }
        autoAnalyses.remove(level.dimension());
        TicketPauseUtil.pauseAutoBucketing(level, false);
        return buildReport(session);
    }

    /**
     * 是否存在活动会话。
     */
    boolean hasActiveSession(ServerLevel level) {
        return sessions.containsKey(level.dimension());
    }

    /**
     * 推进会话并处理定时分析。
     */
    void tickSessions(MinecraftServer server) {
        for (var session : sessions.values()) {
            session.serverTickCount++;
        }
        ChunkPerfSessionController.tickAutoAnalyses(autoAnalyses, server, this::stop);
    }

    /**
     * 判断是否需要跟踪该位置。
     */
    boolean shouldTrack(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        ChunkPerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return false;
        }
        long chunkPos = ChunkPos.asLong(pos);
        return session.targetChunks.contains(chunkPos);
    }

    /**
     * 记录实体 Tick 数据（方块实体也作为实体处理）。
     */
    void onEntityTick(Level level, BlockPos pos, String type, long durationNanos, boolean isBlockEntity) {
        ChunkPerfSession session = resolveSession(level, pos);
        if (session == null) {
            return;
        }
        if (isBlockEntity) {
            recordBlockEntity(session, type, durationNanos);
        } else {
            recordEntity(session, type, durationNanos);
        }
    }

    /**
     * 记录区块 Tick 数据。
     */
    void onChunkTick(Level level, ChunkPos pos, long durationNanos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkPerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return;
        }
        if (!session.targetChunks.contains(pos.toLong())) {
            return;
        }
        recordChunk(session, durationNanos);
    }

    /**
     * 根据方块坐标判断当前 Tick 是否需要被采样。
     */
    private ChunkPerfSession resolveSession(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (pos == null) {
            return null;
        }
        ChunkPerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return null;
        }
        long chunkPos = ChunkPos.asLong(pos);
        if (!session.targetChunks.contains(chunkPos)) {
            return null;
        }
        return session;
    }

    private void recordBlockEntity(ChunkPerfSession session, String type, long durationNanos) {
        session.beTickCount++;
        session.beTotalNanos += durationNanos;
        if (durationNanos > session.beMaxNanos) {
            session.beMaxNanos = durationNanos;
        }
        session.typeTotals.addTo(type, durationNanos);
        session.typeCounts.addTo(type, 1);
    }

    private void recordEntity(ChunkPerfSession session, String type, long durationNanos) {
        session.entityTickCount++;
        session.entityTotalNanos += durationNanos;
        if (durationNanos > session.entityMaxNanos) {
            session.entityMaxNanos = durationNanos;
        }
        session.entityTotals.addTo(type, durationNanos);
        session.entityCounts.addTo(type, 1);
    }

    private void recordChunk(ChunkPerfSession session, long durationNanos) {
        session.chunkTickCount++;
        session.chunkTotalNanos += durationNanos;
        if (durationNanos > session.chunkMaxNanos) {
            session.chunkMaxNanos = durationNanos;
        }
    }

    private Component buildReport(ChunkPerfSession session) {
        long elapsedNanos = System.nanoTime() - session.startedAtNanos;
        return ChunkPerfMessages.buildReport(
                session.dimension,
                session.groupIndex,
                session.ownerLabel,
                session.targetChunks.size(),
                session.blockEntityCount,
                session.entityCount,
                session.serverTickCount,
                session.beTotalNanos,
                session.beMaxNanos,
                session.beTickCount,
                session.entityTotalNanos,
                session.entityMaxNanos,
                session.entityTickCount,
                session.chunkTotalNanos,
                session.chunkMaxNanos,
                session.chunkTickCount,
                session.typeTotals,
                session.typeCounts,
                session.entityTotals,
                session.entityCounts,
                Duration.ofNanos(elapsedNanos)
        );
    }

}
