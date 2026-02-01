package com.moepus.serverwarashi;

import com.moepus.serverwarashi.ticketcontrol.TicketControlManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Event subscriber that delegates ticket control to the manager.
 */
@EventBusSubscriber(modid = Serverwarashi.MODID)
public class TicketManager {
    static int age = 0;

    /**
     * Advances the global tick counter and records tick start for TPS sampling.
     *
     * @param event server tick event
     */
    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        age++;
        TicketControlManager.onServerTickStart();
    }

    /**
     * Records tick end for TPS sampling.
     *
     * @param event server tick event
     */
    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        TicketControlManager.onServerTickEnd();
        com.moepus.serverwarashi.chunkperf.ChunkPerfManager.onServerTick(event.getServer());
    }

    /**
     * Runs per-level ticket control when enabled.
     *
     * @param event level tick event
     */
    @SubscribeEvent
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (!Config.ENABLED.get()) return;

        Level level = event.getLevel();
        if (level.isClientSide) return;

        if (level instanceof ServerLevel serverLevel) {
            TicketControlManager.processTickets(serverLevel, age);
        }
    }
}
