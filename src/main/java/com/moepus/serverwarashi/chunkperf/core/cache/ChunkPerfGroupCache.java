package com.moepus.serverwarashi.chunkperf.core.cache;

import com.moepus.serverwarashi.ChunkLoadInfo;
import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.chunkperf.core.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

/**
 * 分组快照缓存与分组解析逻辑。
 */
public final class ChunkPerfGroupCache {
    private static final long GROUP_CACHE_TTL_MS = 30_000L;
    /**
     * 按维度 + 暂停模式缓存分组快照。
     */
    private final Map<ResourceKey<Level>, EnumMap<ChunkPerfTickets.PauseMode, GroupSnapshot>> groupCache = new HashMap<>();
    /**
     * 按维度维护运行期稳定分组索引。
     */
    private final Map<ResourceKey<Level>, Map<TicketOwner<?>, Integer>> stableGroupIndex = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<Integer, TicketOwner<?>>> stableGroupOwner = new HashMap<>();

    /**
     * 解析分组索引并返回分组条目。
     */
    public GroupLookup resolveGroup(ServerLevel level,
                                    int groupIndex,
                                    ChunkPerfTickets.PauseMode pauseMode,
                                    Component emptyMessage) {
        List<ChunkPerfGroupEntry> groups = getGroupSnapshot(level, pauseMode, true).groups();
        if (groups.isEmpty()) {
            return new GroupLookup(null, emptyMessage);
        }
        ensureStableIndex(level.dimension(), groups);
        TicketOwner<?> owner = getOwnerByIndex(level.dimension(), groupIndex);
        if (owner == null) {
            return new GroupLookup(null, ChunkPerfMessages.groupIndexOutOfRange(getMaxStableIndex(level.dimension())));
        }
        for (ChunkPerfGroupEntry entry : groups) {
            if (entry.owner().equals(owner)) {
                return new GroupLookup(entry, null);
            }
        }
        return new GroupLookup(null, ChunkPerfMessages.groupIndexOutOfRange(getMaxStableIndex(level.dimension())));
    }

    /**
     * 构建分组列表并输出到消息。
     */
    public Component listGroups(ServerLevel level,
                                ChunkPerfTickets.PauseMode pauseMode,
                                String header,
                                ChunkPerfTickets.SortMode sortMode) {
        GroupSnapshot snapshot = getGroupSnapshot(level, pauseMode, false);
        Map<TicketOwner<?>, Integer> indexMap = buildStableIndexMap(level.dimension(), snapshot.groups());
        return ChunkPerfMessages.buildOwnerStatsComponent(
                header,
                snapshot.snapshot(),
                sortMode,
                pauseMode,
                true,
                indexMap
        );
    }

    /**
     * 导出票据列表（可选写入 CSV）。
     */
    public Component dumpTickets(ServerLevel level, boolean currentWorking, boolean saveCsv) {
        ChunkPerfTickets.PauseMode pauseMode = currentWorking
                ? ChunkPerfTickets.PauseMode.ACTIVE_ONLY
                : ChunkPerfTickets.PauseMode.ALL;
        ChunkPerfTickets.OwnerStatsSnapshot snapshot =
                ChunkPerfTickets.collectOwnerStatsSnapshot(level, pauseMode);
        Map<TicketOwner<?>, Integer> indexMap =
                buildStableIndexMap(level.dimension(), buildGroupsFromSnapshot(snapshot));
        if (saveCsv && !snapshot.chunkLoadInfoMap().isEmpty()) {
            ChunkLoadInfo.dumpToCsv(snapshot.chunkLoadInfoMap());
        }
        return ChunkPerfMessages.buildOwnerStatsComponent(
                "Dumped tickets:",
                snapshot,
                ChunkPerfTickets.SortMode.BLOCK_ENTITY,
                pauseMode,
                false,
                indexMap
        );
    }

    /**
     * 获取分组快照（可选复用缓存）。
     */
    public GroupSnapshot getGroupSnapshot(ServerLevel level,
                                          ChunkPerfTickets.PauseMode pauseMode,
                                          boolean preferCache) {
        ResourceKey<Level> dimension = level.dimension();
        EnumMap<ChunkPerfTickets.PauseMode, GroupSnapshot> cache = groupCache.get(dimension);
        long now = System.currentTimeMillis();
        if (preferCache && cache != null) {
            GroupSnapshot cached = cache.get(pauseMode);
            if (cached != null && now - cached.createdAtMillis() <= GROUP_CACHE_TTL_MS) {
                return cached;
            }
        }
        GroupSnapshot fresh = buildGroupSnapshot(level, pauseMode, now);
        if (cache == null) {
            cache = new EnumMap<>(ChunkPerfTickets.PauseMode.class);
            groupCache.put(dimension, cache);
        }
        cache.put(pauseMode, fresh);
        return fresh;
    }

    private GroupSnapshot buildGroupSnapshot(ServerLevel level,
                                             ChunkPerfTickets.PauseMode pauseMode,
                                             long now) {
        ChunkPerfTickets.OwnerStatsSnapshot snapshot =
                ChunkPerfTickets.collectOwnerStatsSnapshot(level, pauseMode);
        List<ChunkPerfGroupEntry> groups = buildGroupsFromSnapshot(snapshot);
        return new GroupSnapshot(snapshot, groups, now);
    }

    private List<ChunkPerfGroupEntry> buildGroupsFromSnapshot(ChunkPerfTickets.OwnerStatsSnapshot snapshot) {
        HashMap<TicketOwner<?>, Set<Long>> ownerMap = snapshot.ownerMap();
        if (ownerMap.isEmpty()) {
            return List.of();
        }
        HashMap<TicketOwner<?>, ChunkPerfTickets.OwnerStats> ownerStats = snapshot.ownerStats();

        List<ChunkPerfGroupEntry> groups = new ArrayList<>();
        ChunkPerfTickets.sortOwnerStats(ownerStats, ChunkPerfTickets.SortMode.BLOCK_ENTITY).stream()
                .map(entry -> new ChunkPerfGroupEntry(entry.getKey(),
                        ownerMap.get(entry.getKey()),
                        entry.getValue().chunkCount(),
                        entry.getValue().blockEntityCount(),
                        entry.getValue().entityCount()))
                .forEach(groups::add);
        return groups;
    }

    private Map<TicketOwner<?>, Integer> buildStableIndexMap(ResourceKey<Level> dimension,
                                                             List<ChunkPerfGroupEntry> groups) {
        ensureStableIndex(dimension, groups);
        Map<TicketOwner<?>, Integer> map = stableGroupIndex.get(dimension);
        if (map == null) {
            return Map.of();
        }
        return new HashMap<>(map);
    }

    private void ensureStableIndex(ResourceKey<Level> dimension, List<ChunkPerfGroupEntry> groups) {
        for (ChunkPerfGroupEntry entry : groups) {
            getOrAssignIndex(dimension, entry.owner());
        }
    }

    private int getOrAssignIndex(ResourceKey<Level> dimension, TicketOwner<?> owner) {
        Map<TicketOwner<?>, Integer> indexMap =
                stableGroupIndex.computeIfAbsent(dimension, key -> new HashMap<>());
        Integer index = indexMap.get(owner);
        if (index != null) {
            return index;
        }
        int nextIndex = indexMap.size();
        indexMap.put(owner, nextIndex);
        stableGroupOwner.computeIfAbsent(dimension, key -> new HashMap<>()).put(nextIndex, owner);
        return nextIndex;
    }

    public int getStableIndex(ResourceKey<Level> dimension, TicketOwner<?> owner) {
        return getOrAssignIndex(dimension, owner);
    }

    private TicketOwner<?> getOwnerByIndex(ResourceKey<Level> dimension, int index) {
        Map<Integer, TicketOwner<?>> ownerMap = stableGroupOwner.get(dimension);
        if (ownerMap == null) {
            return null;
        }
        return ownerMap.get(index);
    }

    private int getMaxStableIndex(ResourceKey<Level> dimension) {
        Map<Integer, TicketOwner<?>> ownerMap = stableGroupOwner.get(dimension);
        if (ownerMap == null || ownerMap.isEmpty()) {
            return -1;
        }
        return ownerMap.keySet().stream().max(Comparator.naturalOrder()).orElse(-1);
    }

    /**
     * 分组解析结果。
     */
    public record GroupLookup(ChunkPerfGroupEntry entry, Component error) {
        public boolean hasError() {
            return error != null;
        }
    }

    /**
     * 分组快照数据。
     */
    public record GroupSnapshot(ChunkPerfTickets.OwnerStatsSnapshot snapshot,
                                List<ChunkPerfGroupEntry> groups,
                                long createdAtMillis) {
    }
}
