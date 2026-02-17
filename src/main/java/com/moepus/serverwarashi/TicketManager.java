package com.moepus.serverwarashi;

import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = Serverwarashi.MODID)
public class TicketManager {
    static int age = 0;
    private static final Set<ResourceKey<Level>> AUTO_BUCKET_PAUSED_LEVELS = new HashSet<>();

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        age++;
    }

    @SubscribeEvent
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        if (level instanceof ServerLevel serverLevel) {
            if (age % Config.RUN_EVERY.get() == 0) {
                applyStoredManualPausedChunks(serverLevel);
            }
            if (!Config.ENABLED.get()) return;
            processTickets(serverLevel);
        }
    }


    private static void processTickets(ServerLevel level) {
        if (AUTO_BUCKET_PAUSED_LEVELS.contains(level.dimension())) {
            return;
        }
        if (age % Config.RUN_EVERY.get() != 0) return;

        ManualPauseData manualData = ManualPauseData.get(level);
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        var tickets = ((DistanceManagerAccessor) distanceManager).getTickets().long2ObjectEntrySet();

        // Collect all tickets
        List<TicketEntry> allTickets = new ArrayList<>();
        for (var entry : tickets) {
            long chunkPosLong = entry.getLongKey();
            if (manualData.contains(chunkPosLong)) {
                continue;
            }
            int chunkX = ChunkPos.getX(chunkPosLong);
            int chunkZ = ChunkPos.getZ(chunkPosLong);

            long morton = morton2D(chunkX, chunkZ);

            for (Ticket<?> ticket : entry.getValue()) {
                if (isSystemTicket(ticket)) continue;
                allTickets.add(new TicketEntry(ticket, morton, chunkPosLong));
            }
        }

        if (allTickets.isEmpty()) return;

        if (Config.PAUSE_ALL_TICKETS.get()) {
            Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
            for (TicketEntry entry : allTickets) {
                if (applyPauseReason(entry.ticket, true, IPauseableTicket.PAUSE_REASON_AUTO)) {
                    modifiedChunks.put(entry.chunkPos, false);
                }
            }
            updateChunkLevel((DistanceManagerAccessor) distanceManager, modifiedChunks);
            return;
        }

        // Sort tickets by Morton code
        allTickets.sort(Comparator.comparingLong(te -> te.morton));
        List<List<TicketEntry>> buckets = divideChunkBuckets(allTickets);

        // Determine active bucket
        int currentGroupIndex = (age / Config.RUN_EVERY.get()) % buckets.size();

        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        for (int i = 0; i < buckets.size(); i++) {
            boolean isActive = (i == currentGroupIndex);
            for (TicketEntry entry : buckets.get(i)) {
                if (applyPauseReason(entry.ticket, !isActive, IPauseableTicket.PAUSE_REASON_AUTO)) {
                    modifiedChunks.computeIfAbsent(entry.chunkPos, k -> isActive);
                }
            }
        }

        // Update chunk levels
        updateChunkLevel((DistanceManagerAccessor) distanceManager, modifiedChunks);
    }

    public static void updateChunkLevel(DistanceManagerAccessor distanceManager, Long2BooleanOpenHashMap modifiedChunks) {
        for (long chunkPos : modifiedChunks.keySet()) {
            SortedArraySet<Ticket<?>> ticketSet = distanceManager.getTickets().get(chunkPos);
            if (ticketSet != null) {
                int newLevel = updateTicketSet(ticketSet);
                distanceManager.getTicketTracker().update(chunkPos, newLevel, modifiedChunks.get(chunkPos));
                if (!ticketSet.isEmpty() && modifiedChunks.get(chunkPos)) {
                    distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, true);
                }
            }
        }
    }

    private static int updateTicketSet(SortedArraySet<Ticket<?>> tickets) {
        List<Ticket<?>> tmp = new ArrayList<>(tickets);
        tickets.clear();
        tickets.addAll(tmp);
        if (tickets.isEmpty()) return 45;
        return tickets.first().getTicketLevel();
    }


    /**
     * 对单张票据应用或取消指定暂停原因的标记。
     */
    public static boolean applyPauseReason(Ticket<?> ticket, boolean paused, int reasonMask) {
        IPauseableTicket pauseable = (IPauseableTicket) (Object) ticket;
        int before = pauseable.serverWarashi$getPauseMask();
        int after = paused ? (before | reasonMask) : (before & ~reasonMask);
        if (before == after) {
            return false;
        }
        pauseable.serverWarashi$setPauseMask(after);
        boolean needUpdate = pauseable.serverWarashi$needUpdate();
        if (needUpdate) {
            pauseable.serverWarashi$clearDirty();
        }
        return needUpdate;
    }

    /**
     * 暂停或恢复当前维度的自动分桶。
     * 暂停时会清理自动暂停标记，避免残留导致区块长期停滞。
     */
    public static void pauseAutoBucketing(ServerLevel level, boolean paused) {
        ResourceKey<Level> dimension = level.dimension();
        if (paused) {
            AUTO_BUCKET_PAUSED_LEVELS.add(dimension);
            clearAutoPause(level);
            return;
        }
        AUTO_BUCKET_PAUSED_LEVELS.remove(dimension);
    }

    /**
     * 清理当前维度的自动暂停标记并更新区块票据层级。
     */
    public static void clearAutoPause(ServerLevel level) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        applyPauseReasonToChunks(
                level,
                accessor.getTickets().keySet(),
                false,
                IPauseableTicket.PAUSE_REASON_AUTO,
                true
        );
    }

    /**
     * 将持久化的手动暂停应用到当前已存在的票据。
     * 只作用于非系统票据。
     */
    public static void applyStoredManualPausedChunks(ServerLevel level) {
        ManualPauseData manualData = ManualPauseData.get(level);
        if (manualData.isEmpty()) {
            return;
        }
        LongIterator iterator = manualData.iterator();
        Iterable<Long> chunks = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Long next() {
                return iterator.nextLong();
            }
        };
        applyPauseReasonToChunks(
                level,
                chunks,
                true,
                IPauseableTicket.PAUSE_REASON_MANUAL,
                false
        );
    }

    /**
     * 对目标区块集合的非系统票据应用暂停原因，并刷新区块 ticket level。
     */
    public static int applyPauseReasonToChunks(ServerLevel level,
                                               Iterable<Long> chunks,
                                               boolean paused,
                                               int reasonMask,
                                               boolean isDecreasing) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        int updated = 0;
        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        for (Long chunkPosObj : chunks) {
            long chunkPos = chunkPosObj;
            SortedArraySet<Ticket<?>> ticketSet = accessor.getTickets().get(chunkPos);
            if (ticketSet == null || ticketSet.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Ticket<?> ticket : ticketSet) {
                if (isSystemTicket(ticket)) {
                    continue;
                }
                if (applyPauseReason(ticket, paused, reasonMask)) {
                    changed = true;
                }
            }
            if (changed) {
                modifiedChunks.put(chunkPos, isDecreasing);
                updated++;
            }
        }
        if (!modifiedChunks.isEmpty()) {
            updateChunkLevel(accessor, modifiedChunks);
        }
        return updated;
    }


    private static @NotNull List<List<TicketEntry>> divideChunkBuckets(List<TicketEntry> allTickets) {
        int targetBucketSize = Config.TICKET_GROUP_SIZE.get();
        int maxBucketSize = targetBucketSize * 2;

        List<List<TicketEntry>> buckets = new ArrayList<>();
        List<TicketEntry> current = new ArrayList<>();

        for (int i = 0; i < allTickets.size(); i++) {
            TicketEntry entry = allTickets.get(i);

            if (current.size() < targetBucketSize) {
                current.add(entry);
            } else {
                boolean tooClose = false;
                if (!current.isEmpty()) {
                    long diff = entry.morton - current.get(current.size() - 1).morton;
                    tooClose = (diff <= Config.PROXIMITY_THRESHOLD.get());
                }

                if (tooClose && current.size() < maxBucketSize) {
                    current.add(entry);
                } else {
                    buckets.add(current);
                    current = new ArrayList<>();
                    current.add(entry);
                }
            }
        }
        if (!current.isEmpty()) {
            buckets.add(current);
        }
        return buckets;
    }

    public static boolean isSystemTicket(Ticket<?> ticket) {
        if (((IPauseableTicket) (Object) ticket).serverWarashi$getLevel() > 32) return true;

        var type = ticket.getType();
        return type == TicketType.START ||
                type == TicketType.PLAYER ||
                type == TicketType.FORCED ||
                type == TicketType.PORTAL ||
                type == TicketType.POST_TELEPORT ||
                type == TicketType.DRAGON;
    }

    private static long morton2D(int x, int z) {
        long xx = Integer.toUnsignedLong(x);
        long zz = Integer.toUnsignedLong(z);
        return interleaveBits(xx) | (interleaveBits(zz) << 1);
    }

    private static long interleaveBits(long x) {
        x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
        x = (x | (x << 8)) & 0x00FF00FF00FF00FFL;
        x = (x | (x << 4)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x << 2)) & 0x3333333333333333L;
        x = (x | (x << 1)) & 0x5555555555555555L;
        return x;
    }

    private record TicketEntry(Ticket<?> ticket, long morton, long chunkPos) {
    }
}
