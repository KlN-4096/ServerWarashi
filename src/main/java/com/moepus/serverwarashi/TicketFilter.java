package com.moepus.serverwarashi;

import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;

/**
 * Ticket filter for grouping and reporting.
 */
public enum TicketFilter {
    /**
     * Non-system tickets only.
     */
    NON_SYSTEM {
        @Override
        public boolean accept(Ticket<?> ticket) {
            if (((IPauseableTicket) (Object) ticket).serverWarashi$getLevel() > 32) {
                return false;
            }
            return !isSystemType(ticket.getType());
        }
    },
    /**
     * System tickets only.
     */
    SYSTEM {
        @Override
        public boolean accept(Ticket<?> ticket) {
            if (((IPauseableTicket) (Object) ticket).serverWarashi$getLevel() > 32) {
                return true;
            }
            return isSystemType(ticket.getType());
        }
    },
    /**
     * All tickets.
     */
    ALL {
        @Override
        public boolean accept(Ticket<?> ticket) {
            return true;
        }
    };

    /**
     * Returns whether the given ticket should be included.
     *
     * @param ticket the ticket to test
     * @return true if accepted
     */
    public abstract boolean accept(Ticket<?> ticket);

    /**
     * Checks if a ticket type is a system type.
     *
     * @param type the ticket type
     * @return true if the type is system-level
     */
    public static boolean isSystemType(TicketType<?> type) {
        return type == TicketType.START
                || type == TicketType.PLAYER
                || type == TicketType.FORCED
                || type == TicketType.PORTAL
                || type == TicketType.POST_TELEPORT
                || type == TicketType.DRAGON;
    }
}
