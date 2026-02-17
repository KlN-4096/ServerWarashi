package com.moepus.serverwarashi.chunkperf.data;

import com.moepus.serverwarashi.ChunkLoadInfo;
import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.chunkperf.ChunkPerfMessages;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChunkPerf 分组视图与查询服务。
 * 负责基于快照生成过滤视图、解析稳定组号并输出列表。
 */
public final class ChunkPerfGroupView {
    private final ChunkPerfSnapshot snapshotCache;
    private final ChunkPerfGroupIndex groupIndex;

    /**
     * 分组解析结果。
     */
    public record GroupLookup(ChunkPerfSnapshot.ChunkPerfGroupEntry entry, Component error) {
    }

    public ChunkPerfGroupView(ChunkPerfSnapshot snapshotCache, ChunkPerfGroupIndex groupIndex) {
        this.snapshotCache = snapshotCache;
        this.groupIndex = groupIndex;
    }

    /**
     * 解析分组索引并返回分组条目。
     */
    public GroupLookup resolveGroup(ServerLevel level,
                                    int groupIndex,
                                    ChunkPerfSnapshot.PauseMode pauseMode) {
        refreshLevel(level);
        ChunkPerfSnapshot.SnapshotData snapshot = view(level, pauseMode);
        List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups = snapshot.groups();
        if (groups.isEmpty()) {
            return new GroupLookup(null, ChunkPerfMessages.noTicketGroupsFound());
        }

        TicketOwner<?> owner = this.groupIndex.getOwnerByIndex(level.dimension(), groupIndex);
        if (owner == null) {
            return new GroupLookup(
                    null,
                    ChunkPerfMessages.groupIndexOutOfRange(this.groupIndex.getMaxStableIndex(level.dimension()))
            );
        }
        for (ChunkPerfSnapshot.ChunkPerfGroupEntry entry : groups) {
            if (entry.owner().equals(owner)) {
                return new GroupLookup(entry, null);
            }
        }
        return new GroupLookup(
                null,
                ChunkPerfMessages.groupIndexOutOfRange(this.groupIndex.getMaxStableIndex(level.dimension()))
        );
    }

    /**
     * 构建分组列表并输出到消息（可选 CSV 输出）。
     */
    public Component listGroups(ServerLevel level,
                                ChunkPerfSnapshot.PauseMode pauseMode,
                                String header,
                                ChunkPerfSnapshot.SortMode sortMode,
                                boolean showActions,
                                boolean saveCsv) {
        refreshLevel(level);
        ChunkPerfSnapshot.SnapshotData snapshot = view(level, pauseMode);
        Map<TicketOwner<?>, Integer> indexMap = groupIndex.getIndexMap(level.dimension());
        if (saveCsv && !snapshot.chunkLoadInfoMap().isEmpty()) {
            ChunkLoadInfo.dumpToCsv(snapshot.chunkLoadInfoMap());
        }
        return ChunkPerfMessages.formatOwnerStatsToComponent(
                header,
                level.dimension(),
                snapshot.groups(),
                sortMode,
                pauseMode,
                showActions,
                indexMap
        );
    }

    /**
     * 获取指定维度在给定过滤模式下的分组视图。
     */
    public List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups(ServerLevel level,
                                                              ChunkPerfSnapshot.PauseMode pauseMode) {
        refreshLevel(level);
        return view(level, pauseMode).groups();
    }

    private void refreshLevel(ServerLevel level) {
        snapshotCache.rebuildLevel(level);
        groupIndex.ensureIndexes(level.dimension(), snapshotCache.getSnapshot(level.dimension()).groups());
    }

    /**
     * 基于缓存快照生成当前视图。
     */
    private ChunkPerfSnapshot.SnapshotData view(ServerLevel level,
                                                ChunkPerfSnapshot.PauseMode pauseMode) {
        ChunkPerfSnapshot.SnapshotData cached = snapshotCache.getSnapshot(level.dimension());
        if (pauseMode == ChunkPerfSnapshot.PauseMode.ALL) {
            return cached;
        }

        List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups = cached.groups().stream()
                .filter(entry -> pauseMode.accept(isGroupPaused(level, entry)))
                .toList();
        HashMap<Long, ChunkLoadInfo> chunkMap = new HashMap<>();
        for (ChunkPerfSnapshot.ChunkPerfGroupEntry entry : groups) {
            for (long chunkPos : entry.chunks()) {
                ChunkLoadInfo info = cached.chunkLoadInfoMap().get(chunkPos);
                if (info != null) {
                    chunkMap.put(chunkPos, info);
                }
            }
        }
        return new ChunkPerfSnapshot.SnapshotData(groups, chunkMap);
    }

    /**
     * 判断分组是否处于暂停状态。
     */
    private boolean isGroupPaused(ServerLevel level, ChunkPerfSnapshot.ChunkPerfGroupEntry entry) {
        DistanceManagerAccessor distanceManager =
                (DistanceManagerAccessor) level.getChunkSource().chunkMap.getDistanceManager();
        for (long chunkPos : entry.chunks()) {
            SortedArraySet<Ticket<?>> tickets = distanceManager.getTickets().get(chunkPos);
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            for (Ticket<?> ticket : tickets) {
                int ticketLevel = ((IPauseableTicket) (Object) ticket).serverWarashi$getLevel();
                if (ticketLevel > 33) {
                    continue;
                }
                if (((IPauseableTicket) (Object) ticket).serverWarashi$isPaused()) {
                    return true;
                }
            }
        }
        return false;
    }
}
