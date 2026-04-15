package com.moepus.serverwarashi.modules.performance.analyze;

import com.moepus.serverwarashi.common.group.ChunkGroupIndex;
import com.moepus.serverwarashi.common.group.ChunkGroupService;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import com.moepus.serverwarashi.modules.performance.report.TicketPerfMessages;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 全分组性能会话跟踪器。
 */
public final class AnalyzeAllGroup extends AnalyzeAbstractGroup<AllGroupSession> {
    private final ChunkGroupIndex groupIndex;

    public AnalyzeAllGroup(ChunkGroupService queryService, ChunkGroupIndex groupIndex) {
        super(queryService);
        this.groupIndex = groupIndex;
    }

    public Component startAll(ServerLevel level, int durationSec, UUID playerId, long sessionId) {
        return startSession(
                level,
                durationSec,
                playerId,
                effectiveDuration -> prepareStart(level, effectiveDuration, sessionId)
        );
    }

    public Component stopAll(ServerLevel level) {
        return stopSession(level);
    }

    private StartResult<AllGroupSession> prepareStart(ServerLevel level,
                                                      int effectiveDuration,
                                                      long sessionId) {
        queryService.refresh(level);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups =
                queryService.groups(level, ChunkGroupSnapshot.PauseMode.ACTIVE_ONLY);
        if (groups.isEmpty()) {
            return startError(TicketPerfMessages.noTicketGroupsFound());
        }

        Long2IntOpenHashMap chunkToGroup = new Long2IntOpenHashMap();
        chunkToGroup.defaultReturnValue(-1);
        for (int i = 0; i < groups.size(); i++) {
            for (long chunkPos : groups.get(i).chunks()) {
                chunkToGroup.put(chunkPos, i);
            }
        }

        AllGroupSession session = new AllGroupSession(sessionId, level.dimension(), groups, chunkToGroup);
        return startSuccess(
                session,
                TicketPerfMessages.allGroupsAnalysisStarted(level.dimension(), groups.size(), effectiveDuration)
        );
    }

    @Override
    protected boolean isTrackedChunk(AllGroupSession session, long chunkPos) {
        return resolveGroupIndex(session, chunkPos) >= 0;
    }

    @Override
    protected void recordEntityTick(AllGroupSession session,
                                    long chunkPos,
                                    String type,
                                    String sourceId,
                                    String sourceLabel,
                                    long durationNanos,
                                    boolean isBlockEntity) {
        int groupIndex = resolveGroupIndex(session, chunkPos);
        if (groupIndex < 0) {
            return;
        }
        if (isBlockEntity) {
            if (groupIndex != session.pendingBlockEntityGroupIndex) {
                flushPendingBlockEntity(session);
                session.pendingBlockEntityGroupIndex = groupIndex;
            }
            session.pendingBlockEntityNanos += durationNanos;
        } else {
            if (groupIndex != session.pendingEntityGroupIndex) {
                flushPendingEntity(session);
                session.pendingEntityGroupIndex = groupIndex;
            }
            session.pendingEntityNanos += durationNanos;
        }
    }

    @Override
    protected void recordChunkTick(AllGroupSession session, ChunkPos pos, long durationNanos) {
        int groupIndex = resolveGroupIndex(session, pos.toLong());
        if (groupIndex < 0) {
            return;
        }
        session.chunkTotalNanos[groupIndex] += durationNanos;
    }

    @Override
    protected void flushPending(AllGroupSession session) {
        flushPendingEntity(session);
        flushPendingBlockEntity(session);
    }

    @Override
    protected Component buildReport(AllGroupSession session) {
        long elapsedNanos = System.nanoTime() - session.startedAtNanos;
        List<TicketPerfMessages.GroupMsptEntry> entries = new ArrayList<>();
        for (int i = 0; i < session.groups.size(); i++) {
            ChunkGroupSnapshot.ChunkGroupEntry entry = session.groups.get(i);
            ChunkGroupSnapshot.OwnerStats stats = entry.stats();
            int stableIndex = groupIndex.getIndex(session.dimension, entry.owner());
            entries.add(new TicketPerfMessages.GroupMsptEntry(
                    stableIndex,
                    entry.owner(),
                    stats.chunkCount(),
                    stats.blockEntityCount(),
                    stats.entityCount(),
                    session.beTotalNanos[i],
                    session.entityTotalNanos[i],
                    session.chunkTotalNanos[i]
            ));
        }
        entries.sort(Comparator.comparingLong(TicketPerfMessages.GroupMsptEntry::totalNanos).reversed());
        return TicketPerfMessages.buildGroupMsptReport(
                session.dimension,
                entries,
                session.serverTickCount,
                Duration.ofNanos(elapsedNanos)
        );
    }

    @Override
    protected Component noActiveSessionMessage() {
        return TicketPerfMessages.noActiveGroupAnalysis();
    }

    private int resolveGroupIndex(AllGroupSession session, long chunkPos) {
        if (chunkPos == session.lastChunkPos) {
            return session.lastGroupIndex;
        }
        int groupIndex = session.chunkToGroupIndex.get(chunkPos);
        session.lastChunkPos = chunkPos;
        session.lastGroupIndex = groupIndex;
        return groupIndex;
    }

    private void flushPendingEntity(AllGroupSession session) {
        if (session.pendingEntityGroupIndex < 0 || session.pendingEntityNanos == 0L) {
            return;
        }
        session.entityTotalNanos[session.pendingEntityGroupIndex] += session.pendingEntityNanos;
        session.pendingEntityNanos = 0L;
        session.pendingEntityGroupIndex = -1;
    }

    private void flushPendingBlockEntity(AllGroupSession session) {
        if (session.pendingBlockEntityGroupIndex < 0 || session.pendingBlockEntityNanos == 0L) {
            return;
        }
        session.beTotalNanos[session.pendingBlockEntityGroupIndex] += session.pendingBlockEntityNanos;
        session.pendingBlockEntityNanos = 0L;
        session.pendingBlockEntityGroupIndex = -1;
    }
}

final class AllGroupSession extends GroupSession {
    public final List<ChunkGroupSnapshot.ChunkGroupEntry> groups;
    public final Long2IntOpenHashMap chunkToGroupIndex;

    public final long[] beTotalNanos;
    public final long[] entityTotalNanos;
    public final long[] chunkTotalNanos;

    public long lastChunkPos = Long.MIN_VALUE;
    public int lastGroupIndex = -1;

    public int pendingEntityGroupIndex = -1;
    public long pendingEntityNanos;

    public int pendingBlockEntityGroupIndex = -1;
    public long pendingBlockEntityNanos;

    public AllGroupSession(long id,
                           ResourceKey<Level> dimension,
                           List<ChunkGroupSnapshot.ChunkGroupEntry> groups,
                           Long2IntOpenHashMap chunkToGroupIndex) {
        super(id, dimension);
        this.groups = groups;
        this.chunkToGroupIndex = chunkToGroupIndex;
        int size = groups.size();
        this.beTotalNanos = new long[size];
        this.entityTotalNanos = new long[size];
        this.chunkTotalNanos = new long[size];
    }
}
