package com.moepus.serverwarashi.ticketcontrol;

import com.moepus.serverwarashi.Config;
import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.ticketgroup.TicketGroupEntry;
import com.moepus.serverwarashi.ticketgroup.TicketGrouping;
import com.moepus.serverwarashi.ticketgroup.TicketGroupingResult;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal implementation for ticket control and adaptive disabling.
 * This class is package-private and must be accessed via {@link TicketControlManager}.
 */
final class TicketControlModule {
    private static final double TPS_DISABLE_THRESHOLD = 15.0;
    private static final double TPS_RECOVER_THRESHOLD = 20.0;

    /**
     * TPS tracker used to decide when to disable or restore groups.
     */
    private final TpsTracker tpsTracker = new TpsTracker(100);
    /**
     * Per-dimension disabled group counts.
     */
    private final Map<ResourceKey<Level>, Integer> disabledGroupCounts = new HashMap<>();
    /**
     * Per-dimension cache of block entity counts per chunk.
     */
    private final Map<ResourceKey<Level>, Long2IntOpenHashMap> blockEntityCache = new HashMap<>();

    /**
     * Records the start of a server tick for TPS sampling.
     */
    void onServerTickStart() {
        tpsTracker.onTickStart();
    }

    /**
     * Records the end of a server tick for TPS sampling.
     */
    void onServerTickEnd() {
        tpsTracker.onTickEnd();
    }

    /**
     * Applies ticket grouping and adaptive pausing for a single level.
     *
     * @param level the level to process
     * @param age   the global tick age counter
     */
    void processTickets(ServerLevel level, int age) {
        if (age % Config.RUN_EVERY.get() != 0) {
            return;
        }

        TicketGroupingResult grouping = TicketGrouping.build(level);
        List<TicketGroupEntry> allTickets = grouping.entries();
        if (allTickets.isEmpty()) {
            return;
        }

        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        if (Config.PAUSE_ALL_TICKETS.get()) {
            pauseAllTickets(distanceManager, allTickets);
            return;
        }

        List<List<TicketGroupEntry>> buckets = grouping.buckets();
        if (buckets.isEmpty()) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        int disabledCount = disabledGroupCounts.getOrDefault(dimension, 0);
        disabledCount = updateDisabledCount(disabledCount, buckets.size(), tpsTracker.getTps());
        disabledGroupCounts.put(dimension, disabledCount);

        Set<Integer> disabledGroups = chooseDisabledGroups(level, buckets, disabledCount);
        List<Integer> enabledGroups = buildEnabledGroups(buckets.size(), disabledGroups);
        int activeGroupIndex = (age / Config.RUN_EVERY.get()) % enabledGroups.size();
        int activeGroupId = enabledGroups.get(activeGroupIndex);

        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        for (int i = 0; i < buckets.size(); i++) {
            boolean isActive = (i == activeGroupId);
            for (TicketGroupEntry entry : buckets.get(i)) {
                IPauseableTicket pauseable = (IPauseableTicket) (Object) entry.ticket();
                pauseable.serverWarashi$setPaused(!isActive);
                if (pauseable.serverWarashi$needUpdate()) {
                    modifiedChunks.computeIfAbsent(entry.chunkPos(), k -> isActive);
                    pauseable.serverWarashi$clearDirty();
                }
            }
        }

        updateChunkLevel((DistanceManagerAccessor) distanceManager, modifiedChunks);
    }

    /**
     * Pauses all non-system tickets and updates ticket trackers immediately.
     *
     * @param distanceManager the distance manager for the level
     * @param allTickets      all non-system tickets
     */
    private void pauseAllTickets(DistanceManager distanceManager, List<TicketGroupEntry> allTickets) {
        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        for (TicketGroupEntry entry : allTickets) {
            IPauseableTicket pauseable = (IPauseableTicket) (Object) entry.ticket();
            pauseable.serverWarashi$setPaused(true);
            if (pauseable.serverWarashi$needUpdate()) {
                modifiedChunks.put(entry.chunkPos(), false);
                pauseable.serverWarashi$clearDirty();
            }
        }
        updateChunkLevel((DistanceManagerAccessor) distanceManager, modifiedChunks);
    }

    /**
     * Updates the disabled group count based on TPS and bucket size.
     *
     * @param current     current disabled group count
     * @param bucketCount total bucket count
     * @param tps         current server TPS estimate
     * @return the updated disabled group count
     */
    private int updateDisabledCount(int current, int bucketCount, double tps) {
        int maxDisabled = Math.max(0, bucketCount - 1);
        if (tps < TPS_DISABLE_THRESHOLD) {
            return Math.min(current + 1, maxDisabled);
        }
        if (tps > TPS_RECOVER_THRESHOLD) {
            return Math.max(current - 1, 0);
        }
        return Math.min(current, maxDisabled);
    }

    /**
     * Chooses groups to disable based on block entity counts.
     *
     * @param level         the level used to query block entities
     * @param buckets       grouped tickets
     * @param disabledCount how many groups should be disabled
     * @return a set of disabled group indexes
     */
    private Set<Integer> chooseDisabledGroups(ServerLevel level, List<List<TicketGroupEntry>> buckets, int disabledCount) {
        if (disabledCount <= 0) {
            return Set.of();
        }

        List<GroupCost> costs = computeGroupCosts(level, buckets);
        costs.sort(Comparator.comparingInt(GroupCost::blockEntityCount).reversed()
                .thenComparingInt(GroupCost::index));

        Set<Integer> disabledGroups = new HashSet<>();
        for (int i = 0; i < disabledCount && i < costs.size(); i++) {
            disabledGroups.add(costs.get(i).index());
        }
        return disabledGroups;
    }

    /**
     * Builds the enabled group list from a disabled group set.
     *
     * @param bucketCount    total bucket count
     * @param disabledGroups disabled group indexes
     * @return enabled group indexes in ascending order
     */
    private List<Integer> buildEnabledGroups(int bucketCount, Set<Integer> disabledGroups) {
        List<Integer> enabled = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            if (!disabledGroups.contains(i)) {
                enabled.add(i);
            }
        }
        return enabled;
    }

    /**
     * Computes per-group block entity counts.
     *
     * @param level   the level to sample from
     * @param buckets grouped tickets
     * @return list of group costs
     */
    private List<GroupCost> computeGroupCosts(ServerLevel level, List<List<TicketGroupEntry>> buckets) {
        List<GroupCost> costs = new ArrayList<>(buckets.size());
        for (int i = 0; i < buckets.size(); i++) {
            int cost = computeBucketBlockEntities(level, buckets.get(i));
            costs.add(new GroupCost(i, cost));
        }
        return costs;
    }

    /**
     * Computes the total block entity count for a bucket, using cached counts for unloaded chunks.
     *
     * @param level  the level to sample from
     * @param bucket the bucket to inspect
     * @return total block entity count
     */
    private int computeBucketBlockEntities(ServerLevel level, List<TicketGroupEntry> bucket) {
        LongOpenHashSet chunkPositions = new LongOpenHashSet();
        for (TicketGroupEntry entry : bucket) {
            chunkPositions.add(entry.chunkPos());
        }

        Long2IntOpenHashMap cache = getBlockEntityCache(level.dimension());
        int total = 0;
        for (long chunkPosLong : chunkPositions) {
            ChunkPos pos = new ChunkPos(chunkPosLong);
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk != null) {
                int count = chunk.getBlockEntitiesPos().size();
                cache.put(chunkPosLong, count);
                total += count;
            } else if (cache.containsKey(chunkPosLong)) {
                total += cache.get(chunkPosLong);
            }
        }
        return total;
    }

    /**
     * Resolves the per-dimension cache of block entity counts.
     *
     * @param dimension dimension key for the level
     * @return a cache map for chunk positions
     */
    private Long2IntOpenHashMap getBlockEntityCache(ResourceKey<Level> dimension) {
        return blockEntityCache.computeIfAbsent(dimension, key -> new Long2IntOpenHashMap());
    }

    /**
     * Updates ticket tracker levels for modified chunks.
     *
     * @param distanceManager distance manager accessor
     * @param modifiedChunks  chunks that had ticket state changes
     */
    private void updateChunkLevel(DistanceManagerAccessor distanceManager, Long2BooleanOpenHashMap modifiedChunks) {
        for (long chunkPos : modifiedChunks.keySet()) {
            var ticketSet = distanceManager.getTickets().get(chunkPos);
            if (ticketSet != null) {
                int newLevel = updateTicketSet(ticketSet);
                boolean isDecreasing = modifiedChunks.get(chunkPos);
                distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);
                if (!ticketSet.isEmpty() && isDecreasing) {
                    distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, true);
                }
            }
        }
    }

    /**
     * Rebuilds ticket ordering and returns the current ticket level.
     *
     * @param tickets ticket set to refresh
     * @return the current ticket level
     */
    private int updateTicketSet(SortedArraySet<Ticket<?>> tickets) {
        List<Ticket<?>> tmp = new ArrayList<>(tickets);
        tickets.clear();
        tickets.addAll(tmp);
        return getTicketLevel(tickets);
    }

    /**
     * Returns the current ticket level for a ticket set.
     *
     * @param tickets ticket set
     * @return the ticket level used by the tracker
     */
    private int getTicketLevel(SortedArraySet<Ticket<?>> tickets) {
        if (tickets.isEmpty()) {
            return 45;
        }
        return tickets.first().getTicketLevel();
    }

    /**
     * Cost record for sorting buckets by block entity count.
     *
     * @param index             bucket index
     * @param blockEntityCount  total block entity count
     */
    private record GroupCost(int index, int blockEntityCount) {
    }
}
