package com.moepus.serverwarashi.chunkperf.data;

import com.moepus.serverwarashi.ChunkLoadInfo;
import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.mixin.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.mixin.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.mixin.ServerLevelAcessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChunkPerf 运行期快照缓存与构建器。
 * 该类在服务器运行期保持单实例，按维度缓存快照数据。
 */
public class ChunkPerfSnapshot {
    private final Map<ResourceKey<Level>, SnapshotData> dimensionSnapshots = new HashMap<>();

    /**
     * 票据分组条目：包含 owner 对应的区块集合与统计信息。
     */
    public record ChunkPerfGroupEntry(
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
            List<ChunkPerfGroupEntry> groups,
            HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap
    ) {
    }

    /**
     * 暂停状态过滤模式。
     */
    public enum PauseMode {
        ACTIVE_ONLY,
        PAUSED_ONLY,
        ALL;

        public boolean accept(boolean paused) {
            return switch (this) {
                case ACTIVE_ONLY -> !paused;
                case PAUSED_ONLY -> paused;
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
     * 服务器启动完成后，按维度重建快照。
     */
    public void rebuildAll(MinecraftServer server) {
        dimensionSnapshots.clear();
        for (ServerLevel level : server.getAllLevels()) {
            rebuildLevel(level);
        }
    }

    /**
     * 重建单个维度快照。
     */
    public void rebuildLevel(ServerLevel level) {
        SnapshotData snapshot = collectOwnerStatsSnapshot(level, PauseMode.ALL);
        dimensionSnapshots.put(level.dimension(), snapshot);
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
        SnapshotData snapshot = dimensionSnapshots.get(dimension);
        if (snapshot != null) {
            return snapshot;
        }
        return new SnapshotData(List.of(), new HashMap<>());
    }

    /**
     * 构建拥有者统计快照视图。
     */
    public static SnapshotData collectOwnerStatsSnapshot(
            ServerLevel level,
            PauseMode pauseMode
    ) {
        HashMap<TicketOwner<?>, Set<Long>> ownerMap =
                collectTicketOwners(level, pauseMode);
        HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();
        List<ChunkPerfGroupEntry> groups = List.of();
        if (!ownerMap.isEmpty()) {
            PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAcessor) level).getEntityManager();
            PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
            EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
            EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;
            HashMap<TicketOwner<?>, OwnerStats> ownerStatsMap =
                    collectOwnerStats(level, ownerMap, sectionAccessor, sectionStorage, chunkLoadInfoMap);
            groups = sortOwnerStats(ownerStatsMap, SortMode.BLOCK_ENTITY).stream()
                    .map(entry -> new ChunkPerfGroupEntry(
                            entry.getKey(),
                            ownerMap.get(entry.getKey()),
                            entry.getValue()))
                    .toList();
        }
        return new SnapshotData(groups, chunkLoadInfoMap);
    }

    /**
     * 收集票据拥有者及其区块集合。
     */
    private static HashMap<TicketOwner<?>, Set<Long>> collectTicketOwners(
            ServerLevel level,
            PauseMode pauseMode
    ) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        HashMap<TicketOwner<?>, Set<Long>> ownerMap = new HashMap<>();

        for (Long2ObjectMap.Entry<ChunkHolder> entry : chunkMap.visibleChunkMap.long2ObjectEntrySet()) {
            long chunkPos = entry.getLongKey();
            DistanceManagerAccessor distanceManager = (DistanceManagerAccessor) chunkMap.getDistanceManager();
            var tickets = distanceManager.getTickets().get(chunkPos);
            if (tickets == null || tickets.isEmpty()) continue;
            Ticket<?> ticket = selectTicket(tickets, pauseMode);
            if (ticket == null) continue;

            TicketOwner<?> owner = new TicketOwner<>(ticket, level);
            String ownerName = owner.getName();
            if ("unknown".equals(ownerName)
                    || "Unknown block".equals(ownerName)
                    || "Unknown entity".equals(ownerName)) {
                continue;
            }
            ownerMap.computeIfAbsent(owner, k -> new HashSet<>()).add(chunkPos);
        }

        return ownerMap;
    }

    /**
     * 汇总每个拥有者的统计信息。
     */
    private static HashMap<TicketOwner<?>, OwnerStats> collectOwnerStats(
            ServerLevel level,
            HashMap<TicketOwner<?>, Set<Long>> ownerMap,
            EntitySectionStorageAccessor sectionAccessor,
            EntitySectionStorage<?> sectionStorage,
            HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap
    ) {
        HashMap<TicketOwner<?>, OwnerStats> result = new HashMap<>();

        for (var entry : ownerMap.entrySet()) {
            TicketOwner<?> owner = entry.getKey();
            Set<Long> chunks = entry.getValue();

            int totalBlockEntities = 0;
            for (long chunkPos : chunks) {
                ChunkPos pos = new ChunkPos(chunkPos);
                ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    int chunkBlockEntities = chunk.getBlockEntitiesPos().size();
                    totalBlockEntities += chunkBlockEntities;
                    chunkLoadInfoMap.computeIfAbsent(chunkPos, k -> new ChunkLoadInfo()).blockEntityCount = chunkBlockEntities;
                }
            }

            int totalEntities = 0;
            for (long chunkPos : chunks) {
                ChunkPos pos = new ChunkPos(chunkPos);
                AtomicInteger chunkEntities = new AtomicInteger();
                sectionAccessor.invokeGetChunkSections(pos.x, pos.z).forEach(sectionIndex -> {
                    EntitySection<?> section = sectionStorage.getSection(sectionIndex);
                    if (section == null) return;
                    chunkEntities.addAndGet(section.size());
                });
                totalEntities += chunkEntities.get();
                chunkLoadInfoMap.computeIfAbsent(chunkPos, k -> new ChunkLoadInfo()).entityCount = chunkEntities.get();
            }

            result.put(owner, new OwnerStats(chunks.size(), totalBlockEntities, totalEntities));
        }

        return result;
    }

    /**
     * 按指定模式排序拥有者统计。
     */
    private static List<Map.Entry<TicketOwner<?>, OwnerStats>> sortOwnerStats(
            HashMap<TicketOwner<?>, OwnerStats> ownerStats,
            SortMode sortMode
    ) {
        List<Map.Entry<TicketOwner<?>, OwnerStats>> entries = new ArrayList<>(ownerStats.entrySet());
        Comparator<Map.Entry<TicketOwner<?>, OwnerStats>> comparator = switch (sortMode) {
            case ENTITY -> Comparator
                    .comparingInt((Map.Entry<TicketOwner<?>, OwnerStats> e) -> e.getValue().entityCount())
                    .reversed()
                    .thenComparing(e -> e.getKey().toString());
            case BLOCK_ENTITY -> Comparator
                    .comparingInt((Map.Entry<TicketOwner<?>, OwnerStats> e) -> e.getValue().blockEntityCount())
                    .reversed()
                    .thenComparing(e -> e.getKey().toString());
        };
        entries.sort(comparator);
        return entries;
    }

    /**
     * 从票据集合中选择一个符合暂停过滤条件的票据。
     */
    private static Ticket<?> selectTicket(SortedArraySet<Ticket<?>> tickets,
                                          PauseMode pauseMode) {
        for (Ticket<?> ticket : tickets) {
            boolean paused = ((IPauseableTicket) (Object) ticket).serverWarashi$isPaused();
            if (!pauseMode.accept(paused)) {
                continue;
            }
            int level = ((IPauseableTicket) (Object) ticket).serverWarashi$getLevel();
            if (level > 33) {
                continue;
            }
            return ticket;
        }
        return null;
    }
}
