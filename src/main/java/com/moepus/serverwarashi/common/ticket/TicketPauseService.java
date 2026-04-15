package com.moepus.serverwarashi.common.ticket;

import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

import java.util.ArrayList;
import java.util.List;

public final class TicketPauseService {

    public static void clearAutoPause(ServerLevel level) {
        applyPauseReasonToChunks(
                level,
                TicketUtils.getTickets(TicketUtils.getDistanceManager(level)).keySet(),
                false,
                IPauseableTicket.PAUSE_REASON_AUTO
        );
    }

    public static int applyPauseReasonToChunks(ServerLevel level,
                                        Iterable<Long> chunks,
                                        boolean paused,
                                        int reasonMask) {
        DistanceManagerAccessor accessor = TicketUtils.getDistanceManager(level);
        int updated = 0;
        LongOpenHashSet modifiedChunks = new LongOpenHashSet();
        for (Long chunkPosObj : chunks) {
            long chunkPos = chunkPosObj;
            SortedArraySet<Ticket<?>> ticketSet = TicketUtils.getTickets(accessor).get(chunkPos);
            if (ticketSet == null || ticketSet.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Ticket<?> ticket : ticketSet) {
                if (TicketUtils.isSystemTicket(ticket)) {
                    continue;
                }
                if (updateTicketPauseReason(ticket, paused, reasonMask)) {
                    changed = true;
                }
            }
            if (changed) {
                modifiedChunks.add(chunkPos);
                updated++;
            }
        }
        if (!modifiedChunks.isEmpty()) {
            updateChunkLevel(accessor, modifiedChunks);
        }
        return updated;
    }

    /**
     * 重排 ticketSet 并根据实际 level 变化更新 tracker。
     */
    public static void updateChunkLevel(DistanceManagerAccessor distanceManager, LongOpenHashSet modifiedChunks) {
        for (long chunkPos : modifiedChunks) {
            SortedArraySet<Ticket<?>> ticketSet = distanceManager.getTickets().get(chunkPos);
            if (ticketSet == null) {
                continue;
            }
            int originalLevel = getOriginalTicketLevel(ticketSet);
            resortTicketSet(ticketSet);
            int newLevel = getTicketLevel(ticketSet);
            boolean isDecreasing = newLevel <= originalLevel;
            distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);
            if (!ticketSet.isEmpty() && isDecreasing) {
                distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, true);
            }
        }
    }

    private static int getTicketLevel(SortedArraySet<Ticket<?>> tickets) {
        if (tickets.isEmpty()) return 45;
        return tickets.first().getTicketLevel();
    }

    private static int getOriginalTicketLevel(SortedArraySet<Ticket<?>> tickets) {
        if (tickets.isEmpty()) return 45;
        return ((IPauseableTicket) (Object) tickets.first()).serverWarashi$getLevel();
    }

    private static void resortTicketSet(SortedArraySet<Ticket<?>> tickets) {
        List<Ticket<?>> tmp = new ArrayList<>(tickets);
        tickets.clear();
        tickets.addAll(tmp);
    }

    //仅修改pauseMask,不修改level
    public static boolean updateTicketPauseReason(Ticket<?>  ticket, boolean paused, int reasonMask) {
        IPauseableTicket ticketInfo=(IPauseableTicket) (Object) ticket;
        int before = ticketInfo.serverWarashi$getPauseMask();
        int after = paused ? (before | reasonMask) : (before & ~reasonMask);
        if (before == after) {
            return false;
        }
        ticketInfo.serverWarashi$setPauseMask(after);
        boolean needUpdate = ticketInfo.serverWarashi$needUpdate();
        if (needUpdate) {
            ticketInfo.serverWarashi$clearDirty();
        }
        return needUpdate;
    }
}
