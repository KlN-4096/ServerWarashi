package com.moepus.serverwarashi.modules.performance.analyze;

import com.moepus.serverwarashi.common.group.ChunkGroupService;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import com.moepus.serverwarashi.modules.performance.report.TicketPerfMessages;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
public final class AnalyzeSingleGroup extends AnalyzeAbstractGroup<SingleGroupSession> {
    public AnalyzeSingleGroup(ChunkGroupService queryService) {
        super(queryService);
    }

    public Component start(ServerLevel sourceLevel,
                           int groupIndex,
                           int durationSec,
                           UUID playerId,
                           long sessionId) {
        return startSession(
                sourceLevel,
                durationSec,
                playerId,
                effectiveDuration -> prepareStart(sourceLevel, groupIndex, effectiveDuration, sessionId)
        );
    }

    public Component stop(ServerLevel level) {
        return stopSession(level);
    }

    private StartResult<SingleGroupSession> prepareStart(ServerLevel sourceLevel,
                                                         int groupIndex,
                                                         int effectiveDuration,
                                                         long sessionId) {
        queryService.refresh(sourceLevel);
        ChunkGroupService.GroupLookup lookup = queryService.resolveGroup(
                sourceLevel,
                groupIndex,
                ChunkGroupSnapshot.PauseMode.ACTIVE_ONLY
        );
        if (lookup.failure() != null) {
            return startError(toLookupError(lookup.failure()));
        }

        ChunkGroupSnapshot.ChunkGroupEntry entry = lookup.entry();
        LongOpenHashSet targetChunks = new LongOpenHashSet(entry.chunks());
        ChunkGroupSnapshot.OwnerStats stats = entry.stats();
        SingleGroupSession session = new SingleGroupSession(
                sessionId,
                sourceLevel.dimension(),
                groupIndex,
                targetChunks,
                entry.label(),
                stats.blockEntityCount(),
                stats.entityCount()
        );
        return startSuccess(
                session,
                TicketPerfMessages.sessionStarted(
                        sourceLevel.dimension(),
                        entry.owner().asComponent(),
                        groupIndex,
                        stats.chunkCount(),
                        stats.blockEntityCount(),
                        stats.entityCount(),
                        effectiveDuration
                )
        );
    }

    @Override
    protected boolean isTrackedChunk(SingleGroupSession session, long chunkPos) {
        if (chunkPos == session.lastChunkPos) {
            return session.lastChunkTracked;
        }
        boolean tracked = session.targetChunks.contains(chunkPos);
        session.lastChunkPos = chunkPos;
        session.lastChunkTracked = tracked;
        return tracked;
    }

    @Override
    protected void recordEntityTick(SingleGroupSession session,
                                    long chunkPos,
                                    String type,
                                    String sourceId,
                                    String sourceLabel,
                                    long durationNanos,
                                    boolean isBlockEntity) {
        if (isBlockEntity) {
            recordBlockEntity(session, chunkPos, type, sourceId, sourceLabel, durationNanos);
        } else {
            recordEntity(session, chunkPos, type, sourceId, sourceLabel, durationNanos);
        }
    }

    @Override
    protected void recordChunkTick(SingleGroupSession session, ChunkPos pos, long durationNanos) {
        session.chunkTotalNanos += durationNanos;
        if (durationNanos > session.chunkMaxNanos) {
            session.chunkMaxNanos = durationNanos;
        }
    }

    @Override
    protected void flushPending(SingleGroupSession session) {
        flushPendingEntity(session);
        flushPendingBlockEntity(session);
    }

    @Override
    protected Component buildReport(SingleGroupSession session) {
        long elapsedNanos = System.nanoTime() - session.startedAtNanos;
        return TicketPerfMessages.buildReport(new TicketPerfMessages.SingleGroupReport(
                session.dimension, session.groupIndex, session.ownerLabel,
                session.targetChunks.size(), session.blockEntityCount, session.entityCount,
                session.serverTickCount,
                session.beTotalNanos, session.beMaxNanos,
                session.entityTotalNanos, session.entityMaxNanos,
                session.chunkTotalNanos, session.chunkMaxNanos,
                session.chunkLoadTotals,
                session.blockEntitySpikeNanos, session.blockEntitySpikeLabels,
                session.typeTotals,
                session.entitySpikeNanos, session.entitySpikeLabels,
                session.entityTotals,
                Duration.ofNanos(elapsedNanos)
        ));
    }

    @Override
    protected Component noActiveSessionMessage() {
        return TicketPerfMessages.noActiveSession();
    }

    private void recordBlockEntity(SingleGroupSession session,
                                   long chunkPos,
                                   String type,
                                   String sourceId,
                                   String sourceLabel,
                                   long durationNanos) {
        if (durationNanos > session.beMaxNanos) {
            session.beMaxNanos = durationNanos;
        }
        session.typeTotals.addTo(type, durationNanos);
        updateSpike(session.blockEntitySpikeNanos, session.blockEntitySpikeLabels, sourceId, sourceLabel, durationNanos);
        if (session.pendingBlockEntityChunkPos != chunkPos) {
            flushPendingBlockEntity(session);
            session.pendingBlockEntityChunkPos = chunkPos;
        }
        session.pendingBlockEntityNanos += durationNanos;
    }

    private void recordEntity(SingleGroupSession session,
                              long chunkPos,
                              String type,
                              String sourceId,
                              String sourceLabel,
                              long durationNanos) {
        if (durationNanos > session.entityMaxNanos) {
            session.entityMaxNanos = durationNanos;
        }
        session.entityTotals.addTo(type, durationNanos);
        updateSpike(session.entitySpikeNanos, session.entitySpikeLabels, sourceId, sourceLabel, durationNanos);
        if (session.pendingEntityChunkPos != chunkPos) {
            flushPendingEntity(session);
            session.pendingEntityChunkPos = chunkPos;
        }
        session.pendingEntityNanos += durationNanos;
    }

    private void flushPendingEntity(SingleGroupSession session) {
        if (session.pendingEntityNanos == 0L) {
            return;
        }
        session.entityTotalNanos += session.pendingEntityNanos;
        if (session.pendingEntityChunkPos != Long.MIN_VALUE) {
            session.chunkLoadTotals.addTo(session.pendingEntityChunkPos, session.pendingEntityNanos);
        }
        session.pendingEntityNanos = 0L;
        session.pendingEntityChunkPos = Long.MIN_VALUE;
    }

    private void flushPendingBlockEntity(SingleGroupSession session) {
        if (session.pendingBlockEntityNanos == 0L) {
            return;
        }
        session.beTotalNanos += session.pendingBlockEntityNanos;
        if (session.pendingBlockEntityChunkPos != Long.MIN_VALUE) {
            session.chunkLoadTotals.addTo(session.pendingBlockEntityChunkPos, session.pendingBlockEntityNanos);
        }
        session.pendingBlockEntityNanos = 0L;
        session.pendingBlockEntityChunkPos = Long.MIN_VALUE;
    }

    private void updateSpike(it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<String> spikeNanos,
                             java.util.Map<String, String> spikeLabels,
                             String sourceId,
                             String sourceLabel,
                             long durationNanos) {
        long current = spikeNanos.getLong(sourceId);
        if (durationNanos <= current) {
            return;
        }
        spikeNanos.put(sourceId, durationNanos);
        spikeLabels.put(sourceId, sourceLabel);
    }

    private Component toLookupError(ChunkGroupService.LookupFailure failure) {
        return switch (failure.reason()) {
            case NO_GROUPS, CHUNK_NOT_FOUND -> TicketPerfMessages.noTicketGroupsFound();
            case INDEX_OUT_OF_RANGE, INDEX_MAPPING_MISSING ->
                    TicketPerfMessages.groupIndexOutOfRange(failure.maxStableIndex());
        };
    }
}

final class SingleGroupSession extends GroupSession {
    public final int groupIndex;
    public final String ownerLabel;
    public final LongOpenHashSet targetChunks;
    public final int blockEntityCount;
    public final int entityCount;

    public final Object2LongOpenHashMap<String> typeTotals = new Object2LongOpenHashMap<>();
    public final Object2LongOpenHashMap<String> entityTotals = new Object2LongOpenHashMap<>();

    public long lastChunkPos = Long.MIN_VALUE;
    public boolean lastChunkTracked;

    public long pendingEntityChunkPos = Long.MIN_VALUE;
    public long pendingEntityNanos;
    public long pendingBlockEntityChunkPos = Long.MIN_VALUE;
    public long pendingBlockEntityNanos;

    public long beTotalNanos;
    public long beMaxNanos;

    public long entityTotalNanos;
    public long entityMaxNanos;

    public long chunkTotalNanos;
    public long chunkMaxNanos;
    public final Long2LongOpenHashMap chunkLoadTotals = new Long2LongOpenHashMap();
    public final Object2LongOpenHashMap<String> blockEntitySpikeNanos = new Object2LongOpenHashMap<>();
    public final Map<String, String> blockEntitySpikeLabels = new HashMap<>();
    public final Object2LongOpenHashMap<String> entitySpikeNanos = new Object2LongOpenHashMap<>();
    public final Map<String, String> entitySpikeLabels = new HashMap<>();

    public SingleGroupSession(long id,
                              ResourceKey<Level> dimension,
                              int groupIndex,
                              LongOpenHashSet targetChunks,
                              String ownerLabel,
                              int blockEntityCount,
                              int entityCount) {
        super(id, dimension);
        this.groupIndex = groupIndex;
        this.targetChunks = targetChunks;
        this.ownerLabel = ownerLabel;
        this.blockEntityCount = blockEntityCount;
        this.entityCount = entityCount;
    }
}
