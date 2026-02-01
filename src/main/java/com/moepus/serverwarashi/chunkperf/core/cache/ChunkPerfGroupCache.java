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
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return new GroupLookup(null, ChunkPerfMessages.groupIndexOutOfRange(groups.size() - 1));
        }
        return new GroupLookup(groups.get(groupIndex), null);
    }

    /**
     * 构建分组列表并输出到消息。
     */
    public Component listGroups(ServerLevel level,
                                ChunkPerfTickets.PauseMode pauseMode,
                                String header,
                                ChunkPerfTickets.SortMode sortMode) {
        GroupSnapshot snapshot = getGroupSnapshot(level, pauseMode, false);
        return ChunkPerfMessages.buildOwnerStatsComponent(header, snapshot.snapshot(), sortMode);
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
        if (saveCsv && !snapshot.chunkLoadInfoMap().isEmpty()) {
            ChunkLoadInfo.dumpToCsv(snapshot.chunkLoadInfoMap());
        }
        return ChunkPerfMessages.buildOwnerStatsComponent(
                "Dumped tickets:",
                snapshot,
                ChunkPerfTickets.SortMode.BLOCK_ENTITY
        );
    }

    private GroupSnapshot getGroupSnapshot(ServerLevel level,
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

    /**
     * 分组解析结果。
     */
    public record GroupLookup(ChunkPerfGroupEntry entry, Component error) {
        public boolean hasError() {
            return error != null;
        }
    }

    private record GroupSnapshot(ChunkPerfTickets.OwnerStatsSnapshot snapshot,
                                 List<ChunkPerfGroupEntry> groups,
                                 long createdAtMillis) {
    }
}
