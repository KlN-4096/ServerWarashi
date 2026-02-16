package com.moepus.serverwarashi.chunkperf.core.session;

import com.moepus.serverwarashi.chunkperf.core.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupCache;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupEntry;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import com.moepus.serverwarashi.utils.TicketPauseUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全分组性能会话跟踪器。
 */
final class ChunkPerfSessionTrackerAllGroup {
    private final ChunkPerfGroupCache groupCache;
    /**
     * 按维度维护的全分组会话数据。
     */
    private final Map<ResourceKey<Level>, AllGroupSession> groupSessions = new HashMap<>();
    /**
     * 按维度维护的全分组自动分析信息。
     */
    private final Map<ResourceKey<Level>, ChunkPerfSessionController.AutoAnalysis> autoAnalyses = new HashMap<>();

    ChunkPerfSessionTrackerAllGroup(ChunkPerfGroupCache groupCache) {
        this.groupCache = groupCache;
    }

    /**
     * 启动全分组性能分析会话。
     */
    Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        TicketPauseUtil.pauseAutoBucketing(level, true);
        ChunkPerfGroupCache.GroupSnapshot snapshot =
                groupCache.getGroupSnapshot(level, ChunkPerfTickets.PauseMode.ACTIVE_ONLY, false);
        List<ChunkPerfGroupEntry> groups = snapshot.groups();
        if (groups.isEmpty()) {
            TicketPauseUtil.pauseAutoBucketing(level, false);
            return ChunkPerfMessages.noTicketGroupsFound();
        }
        AllGroupSession session = buildAllGroupSession(level.dimension(), groups);
        groupSessions.put(level.dimension(), session);
        if (durationSec > 0) {
            autoAnalyses.put(level.dimension(),
                    new ChunkPerfSessionController.AutoAnalysis(System.currentTimeMillis() + durationSec * 1000L, playerId));
            return ChunkPerfMessages.allGroupsAnalysisStarted(groups.size(), durationSec);
        }
        autoAnalyses.remove(level.dimension());
        return ChunkPerfMessages.allGroupsAnalysisStarted(groups.size(), 0);
    }

    /**
     * 停止全分组会话并生成报告。
     */
    Component stopAll(ServerLevel level) {
        AllGroupSession session = groupSessions.remove(level.dimension());
        if (session == null) {
            return ChunkPerfMessages.noActiveGroupAnalysis();
        }
        autoAnalyses.remove(level.dimension());
        TicketPauseUtil.pauseAutoBucketing(level, false);
        return buildGroupReport(session);
    }

    /**
     * 是否存在活动会话。
     */
    boolean hasActiveSession(ServerLevel level) {
        return groupSessions.containsKey(level.dimension());
    }

    /**
     * 推进会话并处理定时分析。
     */
    void tickSessions(MinecraftServer server) {
        for (var session : groupSessions.values()) {
            session.serverTickCount++;
        }
        ChunkPerfSessionController.tickAutoAnalyses(autoAnalyses, server, this::stopAll);
    }

    /**
     * 判断是否需要跟踪该位置。
     */
    boolean shouldTrack(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (pos == null) {
            return false;
        }
        AllGroupSession session = groupSessions.get(serverLevel.dimension());
        if (session == null) {
            return false;
        }
        return session.chunkToGroupIndex.containsKey(ChunkPos.asLong(pos));
    }

    /**
     * 记录实体 Tick 数据（方块实体也作为实体处理）。
     */
    void onEntityTick(Level level, BlockPos pos, long durationNanos, boolean isBlockEntity) {
        recordAllGroupEntityTick(level, pos, durationNanos, isBlockEntity);
    }

    /**
     * 记录区块 Tick 数据。
     */
    void onChunkTick(Level level, ChunkPos pos, long durationNanos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        recordAllGroupChunkTick(serverLevel, pos, durationNanos);
    }

    private Component buildGroupReport(AllGroupSession session) {
        long elapsedNanos = System.nanoTime() - session.startedAtNanos;
        List<ChunkPerfMessages.GroupMsptEntry> entries = new ArrayList<>();
        for (int i = 0; i < session.groups.size(); i++) {
            ChunkPerfGroupEntry entry = session.groups.get(i);
            int stableIndex = groupCache.getStableIndex(session.dimension, entry.owner());
            entries.add(new ChunkPerfMessages.GroupMsptEntry(
                    stableIndex,
                    entry.owner(),
                    entry.chunkCount(),
                    entry.blockEntityCount(),
                    entry.entityCount(),
                    session.beTotalNanos[i],
                    session.entityTotalNanos[i],
                    session.chunkTotalNanos[i]
            ));
        }
        entries.sort(Comparator.comparingLong(ChunkPerfMessages.GroupMsptEntry::totalNanos).reversed());
        return ChunkPerfMessages.buildGroupMsptReport(
                session.dimension,
                entries,
                session.serverTickCount,
                Duration.ofNanos(elapsedNanos)
        );
    }

    private AllGroupSession buildAllGroupSession(ResourceKey<Level> dimension,
                                                 List<ChunkPerfGroupEntry> groups) {
        Long2IntOpenHashMap chunkToGroup = new Long2IntOpenHashMap();
        chunkToGroup.defaultReturnValue(-1);
        for (int i = 0; i < groups.size(); i++) {
            for (long chunkPos : groups.get(i).chunks()) {
                chunkToGroup.put(chunkPos, i);
            }
        }
        return new AllGroupSession(dimension, groups, chunkToGroup);
    }

    private void recordAllGroupEntityTick(Level level,
                                          BlockPos pos,
                                          long durationNanos,
                                          boolean isBlockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (pos == null) {
            return;
        }
        AllGroupSession session = groupSessions.get(serverLevel.dimension());
        if (session == null) {
            return;
        }
        int groupIndex = session.chunkToGroupIndex.get(ChunkPos.asLong(pos));
        if (groupIndex < 0) {
            return;
        }
        if (isBlockEntity) {
            session.beTotalNanos[groupIndex] += durationNanos;
        } else {
            session.entityTotalNanos[groupIndex] += durationNanos;
        }
    }

    private void recordAllGroupChunkTick(ServerLevel level, ChunkPos pos, long durationNanos) {
        AllGroupSession session = groupSessions.get(level.dimension());
        if (session == null) {
            return;
        }
        int groupIndex = session.chunkToGroupIndex.get(pos.toLong());
        if (groupIndex < 0) {
            return;
        }
        session.chunkTotalNanos[groupIndex] += durationNanos;
    }

    /**
     * 全分组会话数据。
     */
    private static final class AllGroupSession {
        private final ResourceKey<Level> dimension;
        private final List<ChunkPerfGroupEntry> groups;
        private final Long2IntOpenHashMap chunkToGroupIndex;
        private final long startedAtNanos = System.nanoTime();
        private long serverTickCount = 0L;
        private final long[] beTotalNanos;
        private final long[] entityTotalNanos;
        private final long[] chunkTotalNanos;

        private AllGroupSession(ResourceKey<Level> dimension,
                                List<ChunkPerfGroupEntry> groups,
                                Long2IntOpenHashMap chunkToGroupIndex) {
            this.dimension = dimension;
            this.groups = groups;
            this.chunkToGroupIndex = chunkToGroupIndex;
            int size = groups.size();
            this.beTotalNanos = new long[size];
            this.entityTotalNanos = new long[size];
            this.chunkTotalNanos = new long[size];
        }
    }
}
