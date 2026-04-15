package com.moepus.serverwarashi.common.ticket;

import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;

public class TicketUtils {
    public static final int MAX_PAUSEABLE_LEVEL = 32;

    public static boolean isSystemTicket(Ticket<?> ticket) {
        return getOriginLevel(ticket)>MAX_PAUSEABLE_LEVEL || isSystemType(ticket.getType());
    }

    //返回ticket原始level
    public static int getOriginLevel(Ticket<?> ticket) {
        return ((IPauseableTicket) (Object) ticket).serverWarashi$getLevel();
    }

    //返回暂停后的level
    public static int getMaskedLevel(Ticket<?> ticket) {
        return ticket.getTicketLevel();
    }

    private static boolean isSystemType(TicketType<?> type) {
        return type == TicketType.START
                || type == TicketType.PLAYER
                || type == TicketType.FORCED
                || type == TicketType.PORTAL
                || type == TicketType.POST_TELEPORT
                || type == TicketType.DRAGON;
    }

    public static DistanceManagerAccessor getDistanceManager(ServerLevel level) {
        return (DistanceManagerAccessor) level.getChunkSource().chunkMap.getDistanceManager();
    }

    public static Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getTickets(DistanceManagerAccessor accessor) {
        return accessor.getTickets();
    }
}
