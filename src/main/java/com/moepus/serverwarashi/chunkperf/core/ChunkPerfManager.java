package com.moepus.serverwarashi.chunkperf.core;

import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupCache;
import com.moepus.serverwarashi.chunkperf.core.session.ChunkPerfSessionController;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTicketController;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import com.moepus.serverwarashi.utils.TicketPauseUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * ChunkPerf 对外入口：负责协调会话、分组缓存与票据操作。
 */
public final class ChunkPerfManager {
    /**
     * 单例管理器实例。
     */
    private static final ChunkPerfManager INSTANCE = new ChunkPerfManager();
    /**
     * 分组缓存与解析。
     */
    private final ChunkPerfGroupCache groupCache = new ChunkPerfGroupCache();
    /**
     * 采样会话管理。
     */
    private final ChunkPerfSessionController sessionManager = new ChunkPerfSessionController(groupCache);
    /**
     * 票据操作入口。
     */
    private final ChunkPerfTicketController ticketOps = new ChunkPerfTicketController(groupCache);

    private ChunkPerfManager() {
    }

    /**
     * 启动性能会话。
     *
     * @param level      目标世界
     * @param groupIndex 分组索引
     */
    public static Component start(ServerLevel level, int groupIndex) {
        return INSTANCE.sessionManager.start(level, groupIndex, 0, null);
    }

    /**
     * 启动性能会话。
     *
     * @param source     命令来源
     * @param groupIndex 分组索引
     * @param durationSec 持续秒数，{@code 0} 表示不启用定时分析
     */
    public static Component start(CommandSourceStack source, int groupIndex, int durationSec) {
        UUID playerId = null;
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            playerId = sp.getUUID();
        }
        return INSTANCE.sessionManager.start(source.getLevel(), groupIndex, durationSec, playerId);
    }

    /**
     * 启动全分组性能分析会话。
     *
     * @param level 目标世界
     */
    public static Component startAll(ServerLevel level) {
        return INSTANCE.sessionManager.startAll(level, 0, null);
    }

    /**
     * 启动全分组性能分析会话。
     *
     * @param source     命令来源
     * @param durationSec 持续秒数，{@code 0} 表示不启用定时分析
     */
    public static Component startAll(CommandSourceStack source, int durationSec) {
        UUID playerId = null;
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            playerId = sp.getUUID();
        }
        return INSTANCE.sessionManager.startAll(source.getLevel(), durationSec, playerId);
    }

    /**
     * 降低指定分组票据等级（暂停）。
     */
    public static Component lower(ServerLevel level, int groupIndex) {
        return INSTANCE.ticketOps.lower(level, groupIndex);
    }

    /**
     * 恢复指定分组票据等级（取消暂停）。
     */
    public static Component restore(ServerLevel level, int groupIndex) {
        return INSTANCE.ticketOps.restore(level, groupIndex);
    }

    /**
     * 停止会话并生成报告。
     */
    public static Component stop(ServerLevel level) {
        TicketPauseUtil.pauseAutoBucketing(level, false);
        return INSTANCE.sessionManager.stop(level);
    }

    /**
     * 列出分组并写入缓存快照（指定排序）。
     */
    public static Component listGroups(ServerLevel level,
                                       ChunkPerfTickets.PauseMode pauseMode,
                                       String header,
                                       ChunkPerfTickets.SortMode sortMode) {
        return INSTANCE.groupCache.listGroups(level, pauseMode, header, sortMode);
    }

    /**
     * 导出票据列表（可选写入 CSV）。
     */
    public static Component dumpTickets(ServerLevel level, boolean currentWorking, boolean saveCsv) {
        return INSTANCE.groupCache.dumpTickets(level, currentWorking, saveCsv);
    }

    /**
     * 判断是否需要跟踪该位置。
     */
    public static boolean shouldTrack(Level level, BlockPos pos) {
        return INSTANCE.sessionManager.shouldTrack(level, pos);
    }

    /**
     * 推进会话并处理定时分析。
     */
    public static void onServerTick(MinecraftServer server) {
        INSTANCE.sessionManager.tickSessions(server);
    }

    /**
     * 记录实体 Tick 数据（方块实体也作为实体处理）。
     */
    public static void onEntityTick(Level level,
                                    BlockPos pos,
                                    String type,
                                    long durationNanos,
                                    boolean isBlockEntity) {
        INSTANCE.sessionManager.onEntityTick(level, pos, type, durationNanos, isBlockEntity);
    }

    /**
     * 记录区块 Tick 数据。
     */
    public static void onChunkTick(Level level, net.minecraft.world.level.ChunkPos pos, long durationNanos) {
        INSTANCE.sessionManager.onChunkTick(level, pos, durationNanos);
    }

}
