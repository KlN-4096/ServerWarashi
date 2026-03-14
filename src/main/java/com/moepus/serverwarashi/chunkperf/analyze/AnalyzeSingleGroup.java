package com.moepus.serverwarashi.chunkperf.analyze;

import com.moepus.serverwarashi.chunkperf.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupView;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfSnapshot;
import com.moepus.serverwarashi.chunkperf.data.analyze.SingleGroupSession;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.util.UUID;

/**
 * 单分组性能会话跟踪器。
 */
public final class AnalyzeSingleGroup extends AbstractGroupAnalyzer<SingleGroupSession> {
    public AnalyzeSingleGroup(ChunkPerfGroupView groupView) {
        super(groupView);
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
        ChunkPerfGroupView.GroupLookup lookup = groupView.resolveGroup(
                sourceLevel,
                groupIndex,
                ChunkPerfSnapshot.PauseMode.ACTIVE_ONLY
        );
        if (lookup.error() != null) {
            return startError(lookup.error());
        }

        ChunkPerfSnapshot.ChunkPerfGroupEntry entry = lookup.entry();
        LongOpenHashSet targetChunks = new LongOpenHashSet(entry.chunks());
        ChunkPerfSnapshot.OwnerStats stats = entry.stats();
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
                ChunkPerfMessages.sessionStarted(
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
        recordChunk(session, pos.toLong(), durationNanos);
    }

    @Override
    protected void flushPending(SingleGroupSession session) {
        flushPendingEntity(session);
        flushPendingBlockEntity(session);
    }

    @Override
    protected Component buildReport(SingleGroupSession session) {
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
                session.entityTotalNanos,
                session.entityMaxNanos,
                session.chunkTotalNanos,
                session.chunkMaxNanos,
                session.chunkTotals,
                session.blockEntitySpikeNanos,
                session.blockEntitySpikeLabels,
                session.typeTotals,
                session.entitySpikeNanos,
                session.entitySpikeLabels,
                session.entityTotals,
                Duration.ofNanos(elapsedNanos)
        );
    }

    @Override
    protected Component noActiveSessionMessage() {
        return ChunkPerfMessages.noActiveSession();
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

    private void recordChunk(SingleGroupSession session, long chunkPos, long durationNanos) {
        session.chunkTotalNanos += durationNanos;
        session.chunkTotals.addTo(chunkPos, durationNanos);
        if (durationNanos > session.chunkMaxNanos) {
            session.chunkMaxNanos = durationNanos;
        }
    }

    private void flushPendingEntity(SingleGroupSession session) {
        if (session.pendingEntityNanos == 0L) {
            return;
        }
        session.entityTotalNanos += session.pendingEntityNanos;
        session.pendingEntityNanos = 0L;
        session.pendingEntityChunkPos = Long.MIN_VALUE;
    }

    private void flushPendingBlockEntity(SingleGroupSession session) {
        if (session.pendingBlockEntityNanos == 0L) {
            return;
        }
        session.beTotalNanos += session.pendingBlockEntityNanos;
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
}
