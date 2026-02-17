package com.moepus.serverwarashi.chunkperf;

import com.moepus.serverwarashi.chunkperf.analyze.ChunkPerfSessionController;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupIndex;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupView;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfSnapshot;
import com.moepus.serverwarashi.chunkperf.operation.ChunkPerfTicketController;
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
     * 分组快照缓存与构建。
     */
    private final ChunkPerfSnapshot groupCache = new ChunkPerfSnapshot();
    /**
     * 稳定分组索引。
     */
    private final ChunkPerfGroupIndex groupIndex = new ChunkPerfGroupIndex();
    /**
     * 分组查询与视图输出。
     */
    private final ChunkPerfGroupView groupView = new ChunkPerfGroupView(groupCache, groupIndex);
    /**
     * 采样会话管理。
     */
    private final ChunkPerfSessionController sessionManager = new ChunkPerfSessionController(groupView, groupIndex);
    /**
     * 票据操作入口。
     */
    private final ChunkPerfTicketController ticketOps = new ChunkPerfTicketController(groupView);
    private ChunkPerfManager() {
    }

    /**
     * 启动性能会话（可选定时）。
     */
    public static Component start(ServerLevel level, int groupIndex, int durationSec, UUID playerId) {
        return INSTANCE.sessionManager.start(level, groupIndex, durationSec, playerId);
    }

    /**
     * 启动全分组性能分析会话（可选定时）。
     */
    public static Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        return INSTANCE.sessionManager.startAll(level, durationSec, playerId);
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
        return INSTANCE.sessionManager.stop(level);
    }

    /**
     * 列出分组并写入缓存快照（指定排序）。
     */
    public static Component listGroups(ServerLevel level,
                                       ChunkPerfSnapshot.PauseMode pauseMode,
                                       String header,
                                       ChunkPerfSnapshot.SortMode sortMode,
                                       boolean showActions,
                                       boolean saveCsv) {
        return INSTANCE.groupView.listGroups(level, pauseMode, header, sortMode, showActions, saveCsv);
    }

    /**
     * 服务器完全启动后构建快照与索引。
     */
    public static void rebuildSnapshots(MinecraftServer server) {
        INSTANCE.groupCache.rebuildAll(server);
        INSTANCE.groupIndex.clear();
        for (ServerLevel level : server.getAllLevels()) {
            INSTANCE.groupIndex.ensureIndexes(
                    level.dimension(),
                    INSTANCE.groupCache.getSnapshot(level.dimension()).groups()
            );
        }
    }

    /**
     * 服务器停止时清理快照与索引缓存。
     */
    public static void clearSnapshots() {
        INSTANCE.groupCache.clear();
        INSTANCE.groupIndex.clear();
    }

    /**
     * 解析该位置对应的活动会话 ID。
     */
    public static long resolveTrackSessionId(Level level, BlockPos pos) {
        return INSTANCE.sessionManager.resolveTrackSessionId(level, pos);
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
                                    boolean isBlockEntity,
                                    long sessionId) {
        INSTANCE.sessionManager.onEntityTick(level, pos, type, durationNanos, isBlockEntity, sessionId);
    }

    /**
     * 记录区块 Tick 数据。
     */
    public static void onChunkTick(Level level,
                                   net.minecraft.world.level.ChunkPos pos,
                                   long durationNanos,
                                   long sessionId) {
        INSTANCE.sessionManager.onChunkTick(level, pos, durationNanos, sessionId);
    }

}
