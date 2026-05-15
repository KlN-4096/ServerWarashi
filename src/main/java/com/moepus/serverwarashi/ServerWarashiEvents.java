package com.moepus.serverwarashi;

import com.moepus.serverwarashi.modules.bucket.TicketBucketRuntime;
import com.moepus.serverwarashi.modules.performance.TicketPerfRuntime;
import com.moepus.serverwarashi.modules.idlefreeze.IdleFreezeRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 模组统一事件订阅入口。
 * 只负责接收 NeoForge 事件并分发到各模块运行时入口。
 */
@EventBusSubscriber(modid = Serverwarashi.MODID)
public final class ServerWarashiEvents {
    private ServerWarashiEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TicketPerfRuntime.rebuildSnapshots(event.getServer());
        IdleFreezeRuntime.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        TicketBucketRuntime.onServerTickPre();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        TicketPerfRuntime.tickSessions(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            TicketBucketRuntime.onLevelTickPre(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            IdleFreezeRuntime.onPlayerTickPost(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TicketBucketRuntime.clearRuntimeState();
        TicketPerfRuntime.clearSnapshots();
    }
}
