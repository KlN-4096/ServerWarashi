package com.moepus.serverwarashi.common.group;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Chunk 分组查询服务（公共基础设施）。
 * 负责快照刷新、按区块定位分组以及快照视图读取。
 */
public final class ChunkGroupService {
    private static final ChunkGroupSnapshot SNAPSHOT_CACHE = new ChunkGroupSnapshot();

    private ChunkGroupService() {
    }

    // --- 静态访问器 ---

    public static ChunkGroupSnapshot snapshotCache() {
        return SNAPSHOT_CACHE;
    }

    // --- 便捷静态方法（refresh + query） ---

    public static List<ChunkGroupSnapshot.ChunkGroupEntry> listGroups(ServerLevel level,
                                                                       ChunkGroupSnapshot.PauseMode pauseMode) {
        refresh(level);
        return groups(level, pauseMode);
    }

    public static GroupChunkLookup resolveAtChunk(ServerLevel level,
                                                    long chunkPos,
                                                    ChunkGroupSnapshot.PauseMode pauseMode) {
        refresh(level);
        ChunkGroupSnapshot.SnapshotData snapshot = snapshot(level, pauseMode);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = snapshot.groups();
        if (groups.isEmpty()) {
            return new GroupChunkLookup(null, new LookupFailure(LookupFailureReason.NO_GROUPS));
        }
        for (ChunkGroupSnapshot.ChunkGroupEntry entry : groups) {
            if (entry.chunks().contains(chunkPos)) {
                return new GroupChunkLookup(entry, null);
            }
        }
        return new GroupChunkLookup(null, new LookupFailure(LookupFailureReason.CHUNK_NOT_FOUND));
    }

    // --- 查询结果类型 ---

    /**
     * 分组查询失败原因。
     */
    public enum LookupFailureReason {
        NO_GROUPS,
        CHUNK_NOT_FOUND
    }

    /**
     * 结构化查询失败结果。
     */
    public record LookupFailure(LookupFailureReason reason) {
    }

    /**
     * 基于区块位置定位到的分组结果。
     */
    public record GroupChunkLookup(ChunkGroupSnapshot.ChunkGroupEntry entry, LookupFailure failure) {
    }

    // --- 查询与刷新 ---

    public static void refresh(ServerLevel level) {
        ChunkGroupSnapshot.SnapshotData snapshot = ChunkGroupCollector.collect(level, ChunkGroupSnapshot.PauseMode.ALL);
        SNAPSHOT_CACHE.putSnapshot(level.dimension(), snapshot);
    }

    public static ChunkGroupSnapshot.SnapshotData refreshSnapshot(ServerLevel level,
                                                                   ChunkGroupSnapshot.PauseMode pauseMode) {
        if (pauseMode == ChunkGroupSnapshot.PauseMode.ALL) {
            refresh(level);
            return SNAPSHOT_CACHE.getSnapshot(level.dimension());
        }
        return ChunkGroupCollector.collect(level, pauseMode);
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            refresh(level);
        }
    }

    public static ChunkGroupSnapshot.SnapshotData snapshot(ServerLevel level,
                                                            ChunkGroupSnapshot.PauseMode pauseMode) {
        if (pauseMode == ChunkGroupSnapshot.PauseMode.ALL) {
            return SNAPSHOT_CACHE.getSnapshot(level.dimension());
        }
        return ChunkGroupCollector.collect(level, pauseMode);
    }

    public static List<ChunkGroupSnapshot.ChunkGroupEntry> groups(ServerLevel level,
                                                                  ChunkGroupSnapshot.PauseMode pauseMode) {
        return snapshot(level, pauseMode).groups();
    }
}
