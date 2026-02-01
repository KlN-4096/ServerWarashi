package com.moepus.serverwarashi.ticketcontrol;

import net.minecraft.server.level.ServerLevel;

/**
 * Public entry point that wraps the internal ticket control module.
 * External callers should use this manager instead of accessing internal classes.
 */
public final class TicketControlManager {
    /**
     * Internal module that performs the ticket control logic.
     */
    private final TicketControlModule module;
    /**
     * Singleton manager instance used for static delegation.
     */
    private static final TicketControlManager INSTANCE = new TicketControlManager();

    /**
     * Creates a new manager instance.
     */
    private TicketControlManager() {
        this.module = new TicketControlModule();
    }

    /**
     * Records the start of a server tick for TPS sampling.
     */
    public static void onServerTickStart() {
        INSTANCE.module.onServerTickStart();
    }

    /**
     * Records the end of a server tick for TPS sampling.
     */
    public static void onServerTickEnd() {
        INSTANCE.module.onServerTickEnd();
    }

    /**
     * Processes ticket groups for a single server level.
     *
     * @param level the server level to process
     * @param age   the global tick age counter
     */
    public static void processTickets(ServerLevel level, int age) {
        INSTANCE.module.processTickets(level, age);
    }
}
