package com.moepus.serverwarashi.ticketgroup;

import net.minecraft.server.level.Ticket;

/**
 * Represents a ticket entry used for grouping and filtering.
 *
 * @param ticket   the backing ticket
 * @param morton   Morton code for the chunk
 * @param chunkPos packed chunk position
 */
public record TicketGroupEntry(Ticket<?> ticket, long morton, long chunkPos) {
}
