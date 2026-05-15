package com.moepus.serverwarashi.modules.bucket;

import com.moepus.serverwarashi.common.ticket.TicketPauseService;
import com.moepus.serverwarashi.modules.performance.TicketPerfRuntime;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * 自动分桶模块对外入口。
 * 层级：入口层。
 * 上游：Commands / analyze。下游：TicketBucketService。
 */
public final class TicketBucketRuntime {
    private static int tickAge = 0;
    private static final Set<ResourceKey<Level>> ANALYSIS_SUSPENDED_LEVELS = new HashSet<>();
    private static final TicketBucketService SERVICE = new TicketBucketService();

    private TicketBucketRuntime() {
    }

    /**
     * 推进自动分桶运行时使用的全局 tick 计数。
     */
    public static void onServerTickPre() {
        tickAge++;
    }

    /**
     * 在维度 tick 中执行自动分桶。
     *
     * @param level 目标维度
     */
    public static void onLevelTickPre(ServerLevel level) {
        if (TicketPerfRuntime.hasActiveSession(level) && ANALYSIS_SUSPENDED_LEVELS.add(level.dimension())) {
            TicketPauseService.clearAutoPause(level);
            return;
        }
        ANALYSIS_SUSPENDED_LEVELS.remove(level.dimension());
        SERVICE.processTickets(level, tickAge);
    }

    /**
     * 清空自动分桶模块的全部运行时状态。
     */
    public static void clearRuntimeState() {
        tickAge = 0;
        ANALYSIS_SUSPENDED_LEVELS.clear();
    }
}
