package com.moepus.serverwarashi.utils;

import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.ManualPauseData;
import com.moepus.serverwarashi.TicketManager;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 票据暂停相关的工具方法集合。
 * 负责自动分桶暂停、手动暂停的应用与恢复，以及区块票据层级更新。
 */
public final class TicketPauseUtil {
    private static final Set<ResourceKey<Level>> AUTO_BUCKET_PAUSED_LEVELS = new HashSet<>();

    private TicketPauseUtil() {
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
     * 判断当前维度是否暂停自动分桶。
     */
    public static boolean isAutoBucketingPaused(ServerLevel level) {
        return AUTO_BUCKET_PAUSED_LEVELS.contains(level.dimension());
    }

    /**
     * 清理当前维度的自动暂停标记并更新区块票据层级。
     */
    public static void clearAutoPausedTickets(ServerLevel level) {
        clearAutoPause(level);
    }

    /**
     * 判断指定区块是否在手动暂停列表中。
     */
    public static boolean isManualPaused(ServerLevel level, long chunkPos) {
        return ManualPauseData.get(level).contains(chunkPos);
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
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        LongIterator iterator = manualData.iterator();
        while (iterator.hasNext()) {
            long chunkPos = iterator.nextLong();
            SortedArraySet<Ticket<?>> ticketSet = accessor.getTickets().get(chunkPos);
            if (ticketSet == null || ticketSet.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Ticket<?> ticket : ticketSet) {
                if (TicketManager.isSystemTicket(ticket)) {
                    continue;
                }
                if (applyManualPause(ticket, true)) {
                    changed = true;
                }
            }
            if (changed) {
                modifiedChunks.put(chunkPos, false);
            }
        }
        if (!modifiedChunks.isEmpty()) {
            updateChunkLevel(accessor, modifiedChunks);
        }
    }

    /**
     * 对单张票据应用或取消自动暂停标记。
     */
    public static boolean applyAutoPause(Ticket<?> ticket, boolean paused) {
        return applyPauseReason(ticket, paused, IPauseableTicket.PAUSE_REASON_AUTO);
    }

    /**
     * 对单张票据应用或取消手动暂停标记。
     */
    public static boolean applyManualPause(Ticket<?> ticket, boolean paused) {
        return applyPauseReason(ticket, paused, IPauseableTicket.PAUSE_REASON_MANUAL);
    }

    /**
     * 刷新区块的票据层级，并同步更新 TicketTracker 与 TickingTicketsTracker。
     */
    public static void updateChunkLevel(DistanceManagerAccessor distanceManager, long chunkPos, boolean isDecreasing) {
        SortedArraySet<Ticket<?>> ticketSet = distanceManager.getTickets().get(chunkPos);
        if (ticketSet != null) {
            int newLevel = updateTicketSet(ticketSet);
            distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);
            if (!ticketSet.isEmpty() && isDecreasing) {
                distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, true);
            }
        }
    }

    private static boolean applyPauseReason(Ticket<?> ticket, boolean paused, int reasonMask) {
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

    private static void clearAutoPause(ServerLevel level) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        var tickets = accessor.getTickets().long2ObjectEntrySet();
        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        for (var entry : tickets) {
            long chunkPosLong = entry.getLongKey();
            boolean changed = false;
            for (Ticket<?> ticket : entry.getValue()) {
                if (TicketManager.isSystemTicket(ticket)) {
                    continue;
                }
                if (applyAutoPause(ticket, false)) {
                    changed = true;
                }
            }
            if (changed) {
                modifiedChunks.put(chunkPosLong, true);
            }
        }
        if (!modifiedChunks.isEmpty()) {
            updateChunkLevel(accessor, modifiedChunks);
        }
    }

    private static void updateChunkLevel(DistanceManagerAccessor distanceManager, Long2BooleanOpenHashMap modifiedChunks) {
        for (long chunkPos : modifiedChunks.keySet()) {
            boolean isDecreasing = modifiedChunks.get(chunkPos);
            updateChunkLevel(distanceManager, chunkPos, isDecreasing);
        }
    }

    private static int updateTicketSet(SortedArraySet<Ticket<?>> tickets) {
        List<Ticket<?>> tmp = new ArrayList<>(tickets);
        tickets.clear();
        tickets.addAll(tmp);
        return getTicketLevel(tickets);
    }

    private static int getTicketLevel(SortedArraySet<Ticket<?>> tickets) {
        if (tickets.isEmpty()) {
            return 45;
        }
        return tickets.first().getTicketLevel();
    }
}
