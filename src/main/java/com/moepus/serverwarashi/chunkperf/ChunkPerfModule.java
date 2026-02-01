package com.moepus.serverwarashi.chunkperf;

import com.moepus.serverwarashi.ChunkLoadInfo;
import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.TicketFilter;
import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/**
 * Internal implementation for chunk performance detection.
 */
final class ChunkPerfModule {
    /**
     * Active sessions per dimension.
     */
    private final Map<ResourceKey<Level>, PerfSession> sessions = new HashMap<>();
    /**
     * Active auto-analysis sessions per dimension.
     */
    private final Map<ResourceKey<Level>, AutoAnalysis> autoAnalyses = new HashMap<>();

    /**
     * Starts a performance session for the given group index.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to track
     * @return a status component describing the result
     */
    Component start(ServerLevel level, int groupIndex) {
        return start(level, groupIndex, true, 0, null);
    }

    /**
     * Starts a performance session with an optional ignore33 flag.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to track
     * @param ignore33   whether to ignore tickets already at level 33
     * @return a status component describing the result
     */
    Component start(ServerLevel level, int groupIndex, boolean ignore33) {
        return start(level, groupIndex, ignore33, 0, null);
    }

    /**
     * Starts a timed session and auto-reports after the duration.
     *
     * @param sourceLevel the server level to analyze
     * @param groupIndex  the group index to track
     * @param ignore33    whether to ignore tickets already at level 33
     * @param durationSec session duration in seconds
     * @param playerId    player id to receive the report, or null for server console
     * @return a status component describing the result
     */
    Component start(ServerLevel sourceLevel, int groupIndex, boolean ignore33, int durationSec, UUID playerId) {
        List<GroupEntry> groups = buildGroups(sourceLevel, ignore33, false);
        if (groups.isEmpty()) {
            return Component.literal("No ticket groups found.");
        }
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return Component.literal("Group index out of range. Max = " + (groups.size() - 1));
        }
        GroupEntry entry = groups.get(groupIndex);
        LongOpenHashSet targetChunks = new LongOpenHashSet(entry.chunks());
        PerfSession session = new PerfSession(
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
                    new AutoAnalysis(System.currentTimeMillis() + durationSec * 1000L, playerId));
            return Component.literal("Chunk perf analysis started ("
                            + durationSec + "s) >\n")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(entry.owner().asComponent())
                    .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("    (G" + groupIndex
                            + ", chunks=" + entry.chunkCount()
                            + ", BE=" + entry.blockEntityCount()
                            + ", E=" + entry.entityCount()
                            + ").\n").withStyle(ChatFormatting.GRAY));
        } else {
            autoAnalyses.remove(sourceLevel.dimension());
        }
        MutableComponent message = Component.literal("Chunk perf session started >\n")
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(entry.owner().asComponent())
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
        message = message.append(Component.literal("    (G" + groupIndex
                        + ", chunks=" + entry.chunkCount()
                        + ", BE=" + entry.blockEntityCount()
                        + ", E=" + entry.entityCount()
                        + ").\n").withStyle(ChatFormatting.GRAY));
        return message;
    }
    /**
     * Lowers the ticket level for a group index using the mod's pause logic.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to lower
     * @return a status component describing the result
     */
    Component lower(ServerLevel level, int groupIndex) {
        List<GroupEntry> groups = buildGroups(level, true, false);
        if (groups.isEmpty()) {
            return Component.literal("No ticket groups found.");
        }
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return Component.literal("Group index out of range. Max = " + (groups.size() - 1));
        }
        GroupEntry entry = groups.get(groupIndex);
        int changed = pauseTickets(level, entry.chunks());
        return Component.literal("Lowered tickets for G" + groupIndex + " " + entry.label()
                        + " (chunks=" + entry.chunkCount() + ", updated=" + changed + ").")
                .withStyle(ChatFormatting.GREEN);
    }

    /**
     * Lowers the ticket level for a group index with an optional ignore33 flag.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to lower
     * @param ignore33   whether to ignore tickets already at level 33
     * @return a status component describing the result
     */
    Component lower(ServerLevel level, int groupIndex, boolean ignore33) {
        List<GroupEntry> groups = buildGroups(level, ignore33, false);
        if (groups.isEmpty()) {
            return Component.literal("No ticket groups found.");
        }
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return Component.literal("Group index out of range. Max = " + (groups.size() - 1));
        }
        GroupEntry entry = groups.get(groupIndex);
        int changed = pauseTickets(level, entry.chunks());
        return Component.literal("Lowered tickets for G" + groupIndex + " " + entry.label()
                        + " (chunks=" + entry.chunkCount() + ", updated=" + changed + ").")
                .withStyle(ChatFormatting.GREEN);
    }
    /**
     * Stops the current session and returns a report.
     *
     * @param level the server level to report on
     * @return a report component
     */
    Component stop(ServerLevel level) {
        PerfSession session = sessions.remove(level.dimension());
        if (session == null) {
            return Component.literal("No active chunk perf session.");
        }
        autoAnalyses.remove(level.dimension());
        return session.buildReport();
    }

    /**
     * Ticks the auto-analysis scheduler.
     *
     * @param server the minecraft server instance
     */
    void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (autoAnalyses.isEmpty()) {
            return;
        }
        List<ResourceKey<Level>> finished = new ArrayList<>();
        for (var entry : autoAnalyses.entrySet()) {
            if (now < entry.getValue().endAtMillis) {
                continue;
            }
            ResourceKey<Level> dim = entry.getKey();
            ServerLevel level = server.getLevel(dim);
            if (level == null) {
                finished.add(dim);
                continue;
            }
            Component report = stop(level);
            if (entry.getValue().playerId != null) {
                var player = server.getPlayerList().getPlayer(entry.getValue().playerId);
                if (player != null) {
                    player.sendSystemMessage(report);
                } else {
                    server.sendSystemMessage(report);
                }
            } else {
                server.sendSystemMessage(report);
            }
            finished.add(dim);
        }
        for (var dim : finished) {
            autoAnalyses.remove(dim);
        }
    }

    /**
     * Lists available groups and their chunk counts.
     *
     * @param level the server level to inspect
     * @return a component describing the groups
     */
    Component listGroups(ServerLevel level) {
        List<GroupEntry> groups = buildGroups(level, true, false);
        if (groups.isEmpty()) {
            return Component.literal("No ticket groups found.");
        }
        HashMap<TicketOwner<?>, ChunkLoadInfo.OwnerStats> ownerStats = new HashMap<>();
        for (GroupEntry entry : groups) {
            ownerStats.put(entry.owner(), new ChunkLoadInfo.OwnerStats(
                    entry.chunkCount(), entry.blockEntityCount(), entry.entityCount()));
        }
        return ChunkLoadInfo.formatOwnerStatsToComponent("Ticket groups:", ownerStats, true, false);
    }

    /**
     * Lists groups with an optional ignore33 flag.
     *
     * @param level    the server level to inspect
     * @param ignore33 whether to ignore tickets already at level 33
     * @return a component describing the groups
     */
    Component listGroups(ServerLevel level, boolean ignore33) {
        List<GroupEntry> groups = buildGroups(level, ignore33, false);
        if (groups.isEmpty()) {
            return Component.literal("No ticket groups found.");
        }
        HashMap<TicketOwner<?>, ChunkLoadInfo.OwnerStats> ownerStats = new HashMap<>();
        for (GroupEntry entry : groups) {
            ownerStats.put(entry.owner(), new ChunkLoadInfo.OwnerStats(
                    entry.chunkCount(), entry.blockEntityCount(), entry.entityCount()));
        }
        return ChunkLoadInfo.formatOwnerStatsToComponent("Ticket groups:", ownerStats, true, false);
    }

    /**
     * Restores the ticket level for a lowered group index.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to restore
     * @return a status component describing the result
     */
    Component restore(ServerLevel level, int groupIndex) {
        List<GroupEntry> groups = buildGroups(level, false, true);
        if (groups.isEmpty()) {
            return Component.literal("No lowered ticket groups found.");
        }
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return Component.literal("Group index out of range. Max = " + (groups.size() - 1));
        }
        GroupEntry entry = groups.get(groupIndex);
        int changed = restoreTickets(level, entry.chunks());
        return Component.literal("Restored tickets for G" + groupIndex + " " + entry.label()
                        + " (chunks=" + entry.chunkCount() + ", updated=" + changed + ").")
                .withStyle(ChatFormatting.GREEN);
    }
    /**
     * Checks if the given block position should be tracked.
     *
     * @param level the level hosting the block entity
     * @param pos   the block position
     * @return true if tracking is active and the chunk is in the target group
     */
    boolean shouldTrack(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        PerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return false;
        }
        long chunkPos = ChunkPos.asLong(pos);
        return session.targetChunks().contains(chunkPos);
    }

    /**
     * Records a block entity tick measurement.
     *
     * @param level         the level hosting the block entity
     * @param pos           the block position
     * @param type          block entity type identifier
     * @param durationNanos duration in nanoseconds
     */
    void onBlockEntityTick(Level level, BlockPos pos, String type, long durationNanos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (pos == null) {
            return;
        }
        PerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return;
        }
        long chunkPos = ChunkPos.asLong(pos);
        if (!session.targetChunks().contains(chunkPos)) {
            return;
        }
        session.record(type, durationNanos);
    }

    /**
     * Records an entity tick measurement.
     *
     * @param level         the level hosting the entity
     * @param pos           the entity position
     * @param type          entity type identifier
     * @param durationNanos duration in nanoseconds
     */
    void onEntityTick(Level level, BlockPos pos, String type, long durationNanos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (pos == null) {
            return;
        }
        PerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return;
        }
        long chunkPos = ChunkPos.asLong(pos);
        if (!session.targetChunks().contains(chunkPos)) {
            return;
        }
        session.recordEntity(type, durationNanos);
    }

    /**
     * Records a chunk tick measurement.
     *
     * @param level         the level hosting the chunk
     * @param pos           the chunk position
     * @param durationNanos duration in nanoseconds
     */
    void onChunkTick(Level level, ChunkPos pos, long durationNanos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return;
        }
        if (!session.targetChunks().contains(pos.toLong())) {
            return;
        }
        session.recordChunk(durationNanos);
    }

    /**
     * Holds performance data for a tracking session.
     */
    private static final class PerfSession {
        /**
         * Dimension for this session.
         */
        private final ResourceKey<Level> dimension;
        /**
         * Group index being tracked.
         */
        private final int groupIndex;
        /**
         * Owner label for the tracked group.
         */
        private final String ownerLabel;
        /**
         * Set of target chunk positions.
         */
        private final LongOpenHashSet targetChunks;
        /**
         * Block entity count for the tracked group (snapshot).
         */
        private final int blockEntityCount;
        /**
         * Entity count for the tracked group (snapshot).
         */
        private final int entityCount;
        /**
         * Session start timestamp in nanoseconds.
         */
        private final long startedAtNanos;
        /**
         * Total block entity tick time in nanoseconds.
         */
        private long beTotalNanos;
        /**
         * Maximum block entity tick time in nanoseconds.
         */
        private long beMaxNanos;
        /**
         * Block entity tick count.
         */
        private long beTickCount;
        /**
         * Total entity tick time in nanoseconds.
         */
        private long entityTotalNanos;
        /**
         * Maximum entity tick time in nanoseconds.
         */
        private long entityMaxNanos;
        /**
         * Entity tick count.
         */
        private long entityTickCount;
        /**
         * Total chunk tick time in nanoseconds.
         */
        private long chunkTotalNanos;
        /**
         * Maximum chunk tick time in nanoseconds.
         */
        private long chunkMaxNanos;
        /**
         * Chunk tick count.
         */
        private long chunkTickCount;
        /**
         * Block entity totals by type.
         */
        private final Object2LongOpenHashMap<String> typeTotals = new Object2LongOpenHashMap<>();
        /**
         * Block entity tick counts by type.
         */
        private final Object2LongOpenHashMap<String> typeCounts = new Object2LongOpenHashMap<>();
        /**
         * Entity totals by type.
         */
        private final Object2LongOpenHashMap<String> entityTotals = new Object2LongOpenHashMap<>();
        /**
         * Entity tick counts by type.
         */
        private final Object2LongOpenHashMap<String> entityCounts = new Object2LongOpenHashMap<>();

        /**
         * Creates a new session.
         *
         * @param dimension    the dimension key
         * @param groupIndex   the tracked group index
         * @param targetChunks chunk positions to track
         */
        private PerfSession(ResourceKey<Level> dimension,
                            int groupIndex,
                            LongOpenHashSet targetChunks,
                            String ownerLabel,
                            int blockEntityCount,
                            int entityCount) {
            this.dimension = dimension;
            this.groupIndex = groupIndex;
            this.targetChunks = targetChunks;
            this.ownerLabel = ownerLabel;
            this.blockEntityCount = blockEntityCount;
            this.entityCount = entityCount;
            this.startedAtNanos = System.nanoTime();
        }

        /**
         * Returns the tracked chunk set.
         *
         * @return tracked chunk positions
         */
        private LongOpenHashSet targetChunks() {
            return targetChunks;
        }

        /**
         * Records a tick measurement.
         *
         * @param type          block entity type identifier
         * @param durationNanos duration in nanoseconds
         */
        private void record(String type, long durationNanos) {
            beTickCount++;
            beTotalNanos += durationNanos;
            if (durationNanos > beMaxNanos) {
                beMaxNanos = durationNanos;
            }
            typeTotals.addTo(type, durationNanos);
            typeCounts.addTo(type, 1);
        }

        /**
         * Records an entity tick measurement.
         *
         * @param type          entity type identifier
         * @param durationNanos duration in nanoseconds
         */
        private void recordEntity(String type, long durationNanos) {
            entityTickCount++;
            entityTotalNanos += durationNanos;
            if (durationNanos > entityMaxNanos) {
                entityMaxNanos = durationNanos;
            }
            entityTotals.addTo(type, durationNanos);
            entityCounts.addTo(type, 1);
        }

        /**
         * Records a chunk tick measurement.
         *
         * @param durationNanos duration in nanoseconds
         */
        private void recordChunk(long durationNanos) {
            chunkTickCount++;
            chunkTotalNanos += durationNanos;
            if (durationNanos > chunkMaxNanos) {
                chunkMaxNanos = durationNanos;
            }
        }

        /**
         * Builds a report component for this session.
         *
         * @return report component
         */
        private Component buildReport() {
            long elapsedNanos = System.nanoTime() - startedAtNanos;
            double beTotalMs = beTotalNanos / 1_000_000.0;
            double bePerTickMs = beTickCount == 0 ? 0.0 : beTotalMs / beTickCount;
            double beMaxMs = beMaxNanos / 1_000_000.0;
            double entityTotalMs = entityTotalNanos / 1_000_000.0;
            double entityPerTickMs = entityTickCount == 0 ? 0.0 : entityTotalMs / entityTickCount;
            double entityMaxMs = entityMaxNanos / 1_000_000.0;
            double chunkTotalMs = chunkTotalNanos / 1_000_000.0;
            double chunkPerTickMs = chunkTickCount == 0 ? 0.0 : chunkTotalMs / chunkTickCount;
            double chunkMaxMs = chunkMaxNanos / 1_000_000.0;
            double combinedTotalMs = beTotalMs + entityTotalMs + chunkTotalMs;
            long totalTicks = beTickCount + entityTickCount + chunkTickCount;
            double totalPerTickMs = totalTicks == 0 ? 0.0 : combinedTotalMs / totalTicks;
            Duration elapsed = Duration.ofNanos(elapsedNanos);

            MutableComponent root = Component.literal("ServerWarashi Profiler Report\n")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("====================================\n").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Target: " + dimension.location() + " | G" + groupIndex + " " + ownerLabel + "\n")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Chunks: " + targetChunks.size()
                            + ", BlockEntity=" + blockEntityCount
                            + ", Entity=" + entityCount
                            + "  | Elapsed: " + elapsed.toSeconds() + "s\n")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("Total: %.3fms, Totalticks: %d, %.6fms/tick\n",
                            combinedTotalMs, totalTicks, totalPerTickMs))
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("---- summary ----\n").withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal(String.format("block entities (%.6fms/tick)>total=%.3fms, max=%.3fms, ticks=%d\n",
                            bePerTickMs, beTotalMs, beMaxMs, beTickCount)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.format("entities (%.6fms/tick)>total=%.3fms, max=%.3fms, ticks=%d\n",
                            entityPerTickMs, entityTotalMs, entityMaxMs, entityTickCount)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(String.format("chunks (%.6fms/tick)>total=%.3fms, max=%.3fms, ticks=%d\n",
                            chunkPerTickMs, chunkTotalMs, chunkMaxMs, chunkTickCount)).withStyle(ChatFormatting.BLUE));

            root = root.append(Component.literal("---- top block entities ----\n").withStyle(ChatFormatting.DARK_GREEN));
            var topBe = typeTotals.object2LongEntrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                    .limit(5)
                    .toList();
            for (var entry : topBe) {
                long count = typeCounts.getLong(entry.getKey());
                double typeMs = entry.getLongValue() / 1_000_000.0;
                double typePerTick = count == 0 ? 0.0 : typeMs / count;
                root = root.append(Component.literal(String.format(" - %s (%.6fms/tick): total=%.3fms, ticks=%d\n",
                        entry.getKey(), typePerTick, typeMs, count)).withStyle(ChatFormatting.GREEN));
            }

            root = root.append(Component.literal("---- top entities ----\n").withStyle(ChatFormatting.GOLD));
            var topEntities = entityTotals.object2LongEntrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                    .limit(5)
                    .toList();
            for (var entry : topEntities) {
                long count = entityCounts.getLong(entry.getKey());
                double typeMs = entry.getLongValue() / 1_000_000.0;
                double typePerTick = count == 0 ? 0.0 : typeMs / count;
                root = root.append(Component.literal(String.format(" - %s (%.6fms/tick): total=%.3fms, ticks=%d\n",
                        entry.getKey(), typePerTick, typeMs, count)).withStyle(ChatFormatting.YELLOW));
            }

            return root;
        }
    }

    /**
     * Builds groups based on the same owner grouping used by /warashi ticket dump.
     *
     * @param level           the server level to inspect
     * @param excludeLevel33  whether to exclude tickets already at level 33
     * @return ordered list of group entries
     */
    private List<GroupEntry> buildGroups(ServerLevel level, boolean excludeLevel33, boolean onlyLevel33) {
        HashMap<TicketOwner<?>, Set<Long>> ownerMap =
                ChunkLoadInfo.collectTicketOwners(level, true, TicketFilter.ALL, excludeLevel33, onlyLevel33);
        if (ownerMap.isEmpty()) {
            return List.of();
        }
        var entityManager = ((com.moepus.serverwarashi.mixin.ServerLevelAcessor) level).getEntityManager();
        var entityAccessor = (com.moepus.serverwarashi.mixin.PersistentEntitySectionManagerAccessor) entityManager;
        var sectionStorage = entityAccessor.getSectionStorage();
        var sectionAccessor = (com.moepus.serverwarashi.mixin.EntitySectionStorageAccessor) sectionStorage;
        HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();
        HashMap<TicketOwner<?>, ChunkLoadInfo.OwnerStats> ownerStats =
                ChunkLoadInfo.collectOwnerStats(level, ownerMap, sectionAccessor, sectionStorage, chunkLoadInfoMap);
        List<GroupEntry> groups = new ArrayList<>();
        ChunkLoadInfo.sortOwnerStats(ownerStats).stream()
                .map(entry -> new GroupEntry(entry.getKey(),
                        ownerMap.get(entry.getKey()),
                        entry.getValue().chunkCount,
                        entry.getValue().blockEntityCount,
                        entry.getValue().entityCount))
                .forEach(groups::add);
        return groups;
    }

    /**
     * Pauses non-system tickets in the given chunk set and updates ticket levels.
     *
     * @param level  the server level to update
     * @param chunks chunk positions to pause
     * @return number of chunks updated
     */
    private int pauseTickets(ServerLevel level, Set<Long> chunks) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        int updated = 0;
        for (long chunkPos : chunks) {
            SortedArraySet<Ticket<?>> ticketSet = accessor.getTickets().get(chunkPos);
            if (ticketSet == null || ticketSet.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Ticket<?> ticket : ticketSet) {
                if (!TicketFilter.NON_SYSTEM.accept(ticket)) {
                    continue;
                }
                IPauseableTicket pauseable = (IPauseableTicket) (Object) ticket;
                pauseable.serverWarashi$setPaused(true);
                if (pauseable.serverWarashi$needUpdate()) {
                    changed = true;
                    pauseable.serverWarashi$clearDirty();
                }
            }
            if (changed) {
                updateChunkLevel(accessor, chunkPos, false);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Restores non-system tickets in the given chunk set and updates ticket levels.
     *
     * @param level  the server level to update
     * @param chunks chunk positions to restore
     * @return number of chunks updated
     */
    private int restoreTickets(ServerLevel level, Set<Long> chunks) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        int updated = 0;
        for (long chunkPos : chunks) {
            SortedArraySet<Ticket<?>> ticketSet = accessor.getTickets().get(chunkPos);
            if (ticketSet == null || ticketSet.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Ticket<?> ticket : ticketSet) {
                if (!TicketFilter.NON_SYSTEM.accept(ticket)) {
                    continue;
                }
                IPauseableTicket pauseable = (IPauseableTicket) (Object) ticket;
                pauseable.serverWarashi$setPaused(false);
                if (pauseable.serverWarashi$needUpdate()) {
                    changed = true;
                    pauseable.serverWarashi$clearDirty();
                }
            }
            if (changed) {
                updateChunkLevel(accessor, chunkPos, true);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Updates the ticket level for a single chunk position.
     *
     * @param distanceManager distance manager accessor
     * @param chunkPos        chunk position
     * @param isDecreasing    whether the level is decreasing
     */
    private void updateChunkLevel(DistanceManagerAccessor distanceManager, long chunkPos, boolean isDecreasing) {
        var ticketSet = distanceManager.getTickets().get(chunkPos);
        if (ticketSet != null) {
            int newLevel = updateTicketSet(ticketSet);
            distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);
            if (!ticketSet.isEmpty() && isDecreasing) {
                distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, true);
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
     * Counts block entities in the given chunk set without forcing loads.
     *
     * @param level  the server level to query
     * @param chunks chunk positions to count
     * @return total block entity count across loaded chunks
     */
    /**
     * Group entry for a ticket owner and its chunk set.
     *
     * @param label  owner label
     * @param chunks chunk positions for the owner
     */
    private record GroupEntry(TicketOwner<?> owner,
                              Set<Long> chunks,
                              int chunkCount,
                              int blockEntityCount,
                              int entityCount) {
        /**
         * Returns the owner label.
         *
         * @return owner label
         */
        String label() {
            return owner.toString();
        }
    }

    /**
     * Auto-analysis metadata.
     */
    private static final class AutoAnalysis {
        private final long endAtMillis;
        private final UUID playerId;

        /**
         * Creates an auto-analysis entry.
         *
         * @param endAtMillis end time in millis
         * @param playerId    player id or null
         */
        private AutoAnalysis(long endAtMillis, UUID playerId) {
            this.endAtMillis = endAtMillis;
            this.playerId = playerId;
        }
    }
}
