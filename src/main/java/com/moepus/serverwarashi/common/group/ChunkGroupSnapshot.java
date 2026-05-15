package com.moepus.serverwarashi.common.group;

import com.moepus.serverwarashi.common.ticket.TicketOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chunk 分组运行期快照缓存容器。
 * 该类在服务器运行期保持单实例，按维度缓存快照数据。
 */
public class ChunkGroupSnapshot {
    public static final SnapshotData EMPTY_SNAPSHOT = new SnapshotData(List.of(), new HashMap<>());

    private final Map<ResourceKey<Level>, SnapshotData> dimensionSnapshots = new HashMap<>();

    /**
     * 票据分组条目：包含 owner 对应的区块集合与统计信息。
     */
    public record ChunkGroupEntry(
            TicketOwner<?> owner,
            Set<Long> chunks,
            OwnerStats stats
    ) {
        public String label() {
            return owner.toString();
        }
    }

    /**
     * 单个拥有者统计。
     */
    public record OwnerStats(int chunkCount, int blockEntityCount, int entityCount) {
    }

    /**
     * 维度快照视图（纯数据）。
     */
    public record SnapshotData(
            List<ChunkGroupEntry> groups,
            HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap
    ) {
    }

    /**
     * 单个区块的负载统计。
     */
    public record ChunkLoadInfo(
            int blockEntityCount,
            int entityCount
    ) {
    }

    /**
     * 暂停状态过滤模式。
     */
    public enum PauseMode {
        ACTIVE_ONLY,
        ALL;

        public boolean accept(boolean paused) {
            return switch (this) {
                case ACTIVE_ONLY -> !paused;
                case ALL -> true;
            };
        }
    }

    /**
     * 统计排序模式。
     */
    public enum SortMode {
        BLOCK_ENTITY,
        ENTITY
    }

    /**
     * 清理运行期缓存。
     */
    public void clear() {
        dimensionSnapshots.clear();
    }

    /**
     * 读取指定维度当前缓存的快照。
     */
    public SnapshotData getSnapshot(ResourceKey<Level> dimension) {
        return dimensionSnapshots.getOrDefault(dimension, EMPTY_SNAPSHOT);
    }

    /**
     * 写入指定维度的快照。
     */
    public void putSnapshot(ResourceKey<Level> dimension, SnapshotData snapshot) {
        dimensionSnapshots.put(dimension, snapshot);
    }
}
