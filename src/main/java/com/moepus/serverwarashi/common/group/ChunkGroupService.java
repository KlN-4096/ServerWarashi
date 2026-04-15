package com.moepus.serverwarashi.common.group;

import com.moepus.serverwarashi.common.ticket.TicketOwner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/**
 * Chunk 分组查询服务（公共基础设施）。
 * 负责快照刷新、分组解析与快照视图读取。
 */
public final class ChunkGroupService {
    private static final ChunkGroupSnapshot SNAPSHOT_CACHE = new ChunkGroupSnapshot();
    private static final ChunkGroupIndex GROUP_INDEX = new ChunkGroupIndex();
    private static final ChunkGroupService INSTANCE = new ChunkGroupService();

    private ChunkGroupService() {
    }

    // --- 静态访问器 ---

    public static ChunkGroupService instance() {
        return INSTANCE;
    }

    public static ChunkGroupSnapshot snapshotCache() {
        return SNAPSHOT_CACHE;
    }

    public static ChunkGroupIndex groupIndex() {
        return GROUP_INDEX;
    }

    // --- 便捷静态方法（refresh + query） ---

    public static List<ChunkGroupSnapshot.ChunkGroupEntry> listGroups(ServerLevel level,
                                                                       ChunkGroupSnapshot.PauseMode pauseMode) {
        INSTANCE.refresh(level);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = INSTANCE.groups(level, pauseMode);
        INSTANCE.indexMap(level, groups);
        return groups;
    }

    public static GroupLookup resolve(ServerLevel level,
                                       int stableIndex,
                                       ChunkGroupSnapshot.PauseMode pauseMode) {
        INSTANCE.refresh(level);
        return INSTANCE.resolveGroup(level, stableIndex, pauseMode);
    }

    public static GroupChunkLookup resolveAtChunk(ServerLevel level,
                                                    long chunkPos,
                                                    ChunkGroupSnapshot.PauseMode pauseMode) {
        INSTANCE.refresh(level);
        return INSTANCE.resolveGroupAtChunk(level, chunkPos, pauseMode);
    }

    // --- 查询结果类型 ---

    /**
     * 分组查询失败原因。
     */
    public enum LookupFailureReason {
        NO_GROUPS,
        INDEX_OUT_OF_RANGE,
        CHUNK_NOT_FOUND,
        INDEX_MAPPING_MISSING
    }

    /**
     * 结构化查询失败结果。
     */
    public record LookupFailure(LookupFailureReason reason, int maxStableIndex) {
    }

    /**
     * 分组解析结果。
     */
    public record GroupLookup(ChunkGroupSnapshot.ChunkGroupEntry entry, LookupFailure failure) {
    }

    /**
     * 基于区块位置定位到的分组结果。
     */
    public record GroupChunkLookup(int groupIndex, ChunkGroupSnapshot.ChunkGroupEntry entry, LookupFailure failure) {
    }

    // --- 实例方法 ---

    public void refresh(ServerLevel level) {
        ChunkGroupSnapshot.SnapshotData snapshot = ChunkGroupCollector.collect(level, ChunkGroupSnapshot.PauseMode.ALL);
        SNAPSHOT_CACHE.putSnapshot(level.dimension(), snapshot);
        GROUP_INDEX.ensureIndexes(level.dimension(), snapshot.groups());
    }

    public ChunkGroupSnapshot.SnapshotData refreshSnapshot(ServerLevel level,
                                                            ChunkGroupSnapshot.PauseMode pauseMode) {
        if (pauseMode == ChunkGroupSnapshot.PauseMode.ALL) {
            refresh(level);
            return SNAPSHOT_CACHE.getSnapshot(level.dimension());
        }
        ChunkGroupSnapshot.SnapshotData snapshot = ChunkGroupCollector.collect(level, pauseMode);
        GROUP_INDEX.ensureIndexes(level.dimension(), snapshot.groups());
        return snapshot;
    }

    public void refreshAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            refresh(level);
        }
    }

    public GroupLookup resolveGroup(ServerLevel level,
                                     int groupIndex,
                                     ChunkGroupSnapshot.PauseMode pauseMode) {
        ChunkGroupSnapshot.SnapshotData snapshot = snapshot(level, pauseMode);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = snapshot.groups();
        if (groups.isEmpty()) {
            return new GroupLookup(null, new LookupFailure(LookupFailureReason.NO_GROUPS, -1));
        }
        GROUP_INDEX.ensureIndexes(level.dimension(), groups);

        TicketOwner<?> owner = GROUP_INDEX.getOwnerByIndex(level.dimension(), groupIndex);
        if (owner == null) {
            return new GroupLookup(null, new LookupFailure(
                    LookupFailureReason.INDEX_OUT_OF_RANGE,
                    GROUP_INDEX.getMaxStableIndex(level.dimension())));
        }
        for (ChunkGroupSnapshot.ChunkGroupEntry entry : groups) {
            if (entry.owner().equals(owner)) {
                return new GroupLookup(entry, null);
            }
        }
        return new GroupLookup(null, new LookupFailure(
                LookupFailureReason.INDEX_OUT_OF_RANGE,
                GROUP_INDEX.getMaxStableIndex(level.dimension())));
    }

    public GroupChunkLookup resolveGroupAtChunk(ServerLevel level,
                                                 long chunkPos,
                                                 ChunkGroupSnapshot.PauseMode pauseMode) {
        ChunkGroupSnapshot.SnapshotData snapshot = snapshot(level, pauseMode);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = snapshot.groups();
        if (groups.isEmpty()) {
            return new GroupChunkLookup(-1, null, new LookupFailure(LookupFailureReason.NO_GROUPS, -1));
        }
        GROUP_INDEX.ensureIndexes(level.dimension(), groups);
        for (ChunkGroupSnapshot.ChunkGroupEntry entry : groups) {
            if (!entry.chunks().contains(chunkPos)) {
                continue;
            }
            int groupIndex = GROUP_INDEX.getIndex(level.dimension(), entry.owner());
            if (groupIndex < 0) {
                return new GroupChunkLookup(-1, null, new LookupFailure(
                        LookupFailureReason.INDEX_MAPPING_MISSING,
                        GROUP_INDEX.getMaxStableIndex(level.dimension())
                ));
            }
            return new GroupChunkLookup(groupIndex, entry, null);
        }
        return new GroupChunkLookup(-1, null, new LookupFailure(
                LookupFailureReason.CHUNK_NOT_FOUND,
                GROUP_INDEX.getMaxStableIndex(level.dimension())
        ));
    }

    public ChunkGroupSnapshot.SnapshotData snapshot(ServerLevel level,
                                                     ChunkGroupSnapshot.PauseMode pauseMode) {
        if (pauseMode == ChunkGroupSnapshot.PauseMode.ALL) {
            return SNAPSHOT_CACHE.getSnapshot(level.dimension());
        }
        return ChunkGroupCollector.collect(level, pauseMode);
    }

    public List<ChunkGroupSnapshot.ChunkGroupEntry> groups(ServerLevel level,
                                                             ChunkGroupSnapshot.PauseMode pauseMode) {
        return snapshot(level, pauseMode).groups();
    }

    public Map<TicketOwner<?>, Integer> indexMap(ServerLevel level,
                                                  List<ChunkGroupSnapshot.ChunkGroupEntry> groups) {
        GROUP_INDEX.ensureIndexes(level.dimension(), groups);
        return GROUP_INDEX.getIndexMap(level.dimension());
    }
}
