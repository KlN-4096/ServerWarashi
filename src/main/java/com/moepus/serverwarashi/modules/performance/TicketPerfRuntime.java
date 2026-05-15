package com.moepus.serverwarashi.modules.performance;

import com.moepus.serverwarashi.common.group.ChunkGroupService;
import com.moepus.serverwarashi.modules.performance.analyze.TicketPerfSessionController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * ChunkPerf 运行时状态与事件入口。
 * 层级：运行时层。
 * 上游：ServerWarashiEvents / mixin hooks。下游：TicketPerfSessionController。
 */
public final class TicketPerfRuntime {
    static final TicketPerfSessionController SESSION_MANAGER =
            new TicketPerfSessionController();

    private TicketPerfRuntime() {
    }

    // --- 生命周期 ---

    public static void rebuildSnapshots(MinecraftServer server) {
        ChunkGroupService.snapshotCache().clear();
        ChunkGroupService.refreshAll(server);
    }

    public static void clearSnapshots() {
        ChunkGroupService.snapshotCache().clear();
        SESSION_MANAGER.clearRuntimeState();
    }

    // --- 事件入口 ---

    public static void tickSessions(MinecraftServer server) {
        SESSION_MANAGER.tickSessions(server);
    }

    // --- Mixin hook 查询 ---

    public static boolean hasActiveSession() {
        return SESSION_MANAGER.hasActiveSession();
    }

    public static boolean hasActiveSession(ServerLevel level) {
        return SESSION_MANAGER.hasActiveSession(level.dimension());
    }

    public static long resolveTrackSessionId(Level level, BlockPos pos) {
        return SESSION_MANAGER.resolveTrackSessionId(level, pos);
    }

    public static void onEntityTick(Level level,
                                    BlockPos pos,
                                    String type,
                                    String sourceId,
                                    String sourceLabel,
                                    long durationNanos,
                                    boolean isBlockEntity,
                                    long sessionId) {
        SESSION_MANAGER.onEntityTick(
                level,
                pos,
                type,
                sourceId,
                sourceLabel,
                durationNanos,
                isBlockEntity,
                sessionId
        );
    }

    public static void onChunkTick(Level level, ChunkPos pos, long durationNanos, long sessionId) {
        SESSION_MANAGER.onChunkTick(level, pos, durationNanos, sessionId);
    }

    // --- 会话控制（Api 委托） ---

    static Component start(ServerLevel sourceLevel, BlockPos pos, int durationSec, UUID playerId) {
        return SESSION_MANAGER.start(sourceLevel, pos, durationSec, playerId);
    }

    static Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        return SESSION_MANAGER.startAll(level, durationSec, playerId);
    }

    static Component stop(ServerLevel level) {
        return SESSION_MANAGER.stop(level);
    }
}
