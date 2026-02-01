package com.moepus.serverwarashi.ticketgroup;

import com.moepus.serverwarashi.Config;
import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility for grouping chunk tickets using the mod's existing logic.
 */
public final class TicketGrouping {

    /**
     * Builds grouped ticket entries for the given level.
     *
     * @param level the server level to analyze
     * @return grouping result containing entries and buckets
     */
    public static TicketGroupingResult build(ServerLevel level) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        var tickets = ((DistanceManagerAccessor) distanceManager).getTickets().long2ObjectEntrySet();

        List<TicketGroupEntry> allTickets = new ArrayList<>();
        for (var entry : tickets) {
            long chunkPosLong = entry.getLongKey();
            int chunkX = ChunkPos.getX(chunkPosLong);
            int chunkZ = ChunkPos.getZ(chunkPosLong);
            long morton = morton2D(chunkX, chunkZ);

            for (Ticket<?> ticket : entry.getValue()) {
                if (isSystemTicket(ticket)) {
                    continue;
                }
                allTickets.add(new TicketGroupEntry(ticket, morton, chunkPosLong));
            }
        }

        allTickets.sort(Comparator.comparingLong(TicketGroupEntry::morton));
        List<List<TicketGroupEntry>> buckets = divideChunkBuckets(allTickets);
        List<LongOpenHashSet> bucketChunks = buildBucketChunks(buckets);
        return new TicketGroupingResult(allTickets, buckets, bucketChunks);
    }

    /**
     * Builds the chunk sets for each bucket.
     *
     * @param buckets bucketed ticket entries
     * @return chunk sets per bucket
     */
    private static List<LongOpenHashSet> buildBucketChunks(List<List<TicketGroupEntry>> buckets) {
        List<LongOpenHashSet> bucketChunks = new ArrayList<>(buckets.size());
        for (List<TicketGroupEntry> bucket : buckets) {
            LongOpenHashSet chunks = new LongOpenHashSet();
            for (TicketGroupEntry entry : bucket) {
                chunks.add(entry.chunkPos());
            }
            bucketChunks.add(chunks);
        }
        return bucketChunks;
    }

    /**
     * Divides tickets into Morton-ordered buckets with proximity padding.
     *
     * @param allTickets sorted ticket entries
     * @return bucketed ticket lists
     */
    private static List<List<TicketGroupEntry>> divideChunkBuckets(List<TicketGroupEntry> allTickets) {
        int targetBucketSize = Config.TICKET_GROUP_SIZE.get();
        int maxBucketSize = targetBucketSize * 2;

        List<List<TicketGroupEntry>> buckets = new ArrayList<>();
        List<TicketGroupEntry> current = new ArrayList<>();

        for (int i = 0; i < allTickets.size(); i++) {
            TicketGroupEntry entry = allTickets.get(i);

            if (current.size() < targetBucketSize) {
                current.add(entry);
            } else {
                boolean tooClose = false;
                if (!current.isEmpty()) {
                    long diff = entry.morton() - current.get(current.size() - 1).morton();
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

    /**
     * Checks whether a ticket is treated as a system ticket and should be ignored.
     *
     * @param ticket the ticket to inspect
     * @return true if the ticket is a system ticket
     */
    private static boolean isSystemTicket(Ticket<?> ticket) {
        if (((IPauseableTicket) (Object) ticket).serverWarashi$getLevel() > 32) {
            return true;
        }

        var type = ticket.getType();
        return type == TicketType.START
                || type == TicketType.PLAYER
                || type == TicketType.FORCED
                || type == TicketType.PORTAL
                || type == TicketType.POST_TELEPORT
                || type == TicketType.DRAGON;
    }

    /**
     * Computes a Morton code for a chunk position.
     *
     * @param x chunk x coordinate
     * @param z chunk z coordinate
     * @return Morton interleaving of x and z
     */
    private static long morton2D(int x, int z) {
        long xx = Integer.toUnsignedLong(x);
        long zz = Integer.toUnsignedLong(z);
        return interleaveBits(xx) | (interleaveBits(zz) << 1);
    }

    /**
     * Interleaves bits for Morton encoding.
     *
     * @param x input value
     * @return interleaved bits
     */
    private static long interleaveBits(long x) {
        x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
        x = (x | (x << 8)) & 0x00FF00FF00FF00FFL;
        x = (x | (x << 4)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x << 2)) & 0x3333333333333333L;
        x = (x | (x << 1)) & 0x5555555555555555L;
        return x;
    }

    /**
     * Utility class.
     */
    private TicketGrouping() {
    }
}
