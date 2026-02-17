package com.moepus.serverwarashi.chunkperf.data;

import com.moepus.serverwarashi.TicketOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChunkPerf 稳定分组索引。
 * 负责在同一服务器生命周期内为 owner 分配稳定组号。
 */
public final class ChunkPerfGroupIndex {
    private final Map<ResourceKey<Level>, Map<TicketOwner<?>, Integer>> stableGroupIndex = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<Integer, TicketOwner<?>>> stableGroupOwner = new HashMap<>();

    /**
     * 清空全部稳定索引。
     */
    public void clear() {
        stableGroupIndex.clear();
        stableGroupOwner.clear();
    }

    /**
     * 确保当前维度所有分组都具备稳定索引。
     */
    public void ensureIndexes(ResourceKey<Level> dimension,
                              List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups) {
        for (ChunkPerfSnapshot.ChunkPerfGroupEntry entry : groups) {
            assignIndex(dimension, entry.owner());
        }
    }

    /**
     * 仅查询稳定索引，不会创建新索引。
     */
    public int getIndex(ResourceKey<Level> dimension, TicketOwner<?> owner) {
        Map<TicketOwner<?>, Integer> indexMap = stableGroupIndex.get(dimension);
        if (indexMap == null) {
            return -1;
        }
        return indexMap.getOrDefault(owner, -1);
    }

    /**
     * 获取当前维度的稳定索引映射。
     */
    public Map<TicketOwner<?>, Integer> getIndexMap(ResourceKey<Level> dimension) {
        return stableGroupIndex.getOrDefault(dimension, Map.of());
    }

    /**
     * 根据稳定索引反查 owner。
     */
    public TicketOwner<?> getOwnerByIndex(ResourceKey<Level> dimension, int index) {
        Map<Integer, TicketOwner<?>> ownerMap = stableGroupOwner.get(dimension);
        if (ownerMap == null) {
            return null;
        }
        return ownerMap.get(index);
    }

    /**
     * 获取稳定索引最大值。
     */
    public int getMaxStableIndex(ResourceKey<Level> dimension) {
        Map<Integer, TicketOwner<?>> ownerMap = stableGroupOwner.get(dimension);
        if (ownerMap == null || ownerMap.isEmpty()) {
            return -1;
        }
        return ownerMap.size() - 1;
    }

    /**
     * 分配稳定索引（仅内部使用）。
     */
    private void assignIndex(ResourceKey<Level> dimension, TicketOwner<?> owner) {
        Map<TicketOwner<?>, Integer> indexMap =
                stableGroupIndex.computeIfAbsent(dimension, key -> new HashMap<>());
        Integer index = indexMap.get(owner);
        if (index != null) {
            return;
        }
        int nextIndex = indexMap.size();
        indexMap.put(owner, nextIndex);
        stableGroupOwner.computeIfAbsent(dimension, key -> new HashMap<>()).put(nextIndex, owner);
    }
}
