package com.moepus.serverwarashi.chunkperf.ticket;

import com.moepus.serverwarashi.ChunkLoadInfo;
import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.mixin.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.mixin.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.mixin.ServerLevelAcessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 票据统计与快照收集工具。
 */
public final class ChunkPerfTickets {
    private ChunkPerfTickets() {
    }

    /**
     * 暂停状态过滤模式。
     */
    public enum PauseMode {
        /**
         * 仅未暂停。
         */
        ACTIVE_ONLY,
        /**
         * 仅已暂停。
         */
        PAUSED_ONLY,
        /**
         * 同时包含已暂停与未暂停。
         */
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
        /**
         * 按方块实体数量排序（默认）。
         */
        BLOCK_ENTITY,
        /**
         * 按实体数量排序。
         */
        ENTITY
    }

    /**
     * 构建拥有者统计快照。
     */
    public static OwnerStatsSnapshot collectOwnerStatsSnapshot(
            ServerLevel level,
            PauseMode pauseMode
    ) {
        HashMap<TicketOwner<?>, Set<Long>> ownerMap =
                collectTicketOwners(level, pauseMode);
        HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();
        HashMap<TicketOwner<?>, OwnerStats> ownerStatsMap = new HashMap<>();
        if (!ownerMap.isEmpty()) {
            PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAcessor) level).getEntityManager();
            PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
            EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
            EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;
            ownerStatsMap = collectOwnerStats(level, ownerMap, sectionAccessor, sectionStorage, chunkLoadInfoMap);
        }
        return new OwnerStatsSnapshot(ownerMap, ownerStatsMap, chunkLoadInfoMap);
    }

    /**
     * 收集票据拥有者及其区块集合。
     */
    public static HashMap<TicketOwner<?>, Set<Long>> collectTicketOwners(
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
            ownerMap.computeIfAbsent(owner, k -> new HashSet<>()).add(chunkPos);
        }

        return ownerMap;
    }

    /**
     * 汇总每个拥有者的统计信息。
     */
    public static HashMap<TicketOwner<?>, OwnerStats> collectOwnerStats(
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
    public static List<Map.Entry<TicketOwner<?>, OwnerStats>> sortOwnerStats(
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

    private static Ticket<?> selectTicket(SortedArraySet<Ticket<?>> tickets,
                                          PauseMode pauseMode) {
        for (Ticket<?> ticket : tickets) {
            boolean paused = ((IPauseableTicket) (Object) ticket).serverWarashi$isPaused();
            if (!pauseMode.accept(paused)) {
                continue;
            }
            return ticket;
        }
        return null;
    }

    /**
     * 单个拥有者的统计数据。
     *
     * @param chunkCount       区块数量
     * @param blockEntityCount 方块实体数量
     * @param entityCount      实体数量
     */
    public record OwnerStats(int chunkCount, int blockEntityCount, int entityCount) {
    }

    /**
     * 拥有者统计快照。
     *
     * @param ownerMap 拥有者与区块集合的映射
     * @param ownerStats 拥有者统计数据（区块/实体/方块实体）
     * @param chunkLoadInfoMap 区块维度的负载统计
     */
    public record OwnerStatsSnapshot(
            HashMap<TicketOwner<?>, Set<Long>> ownerMap,
            HashMap<TicketOwner<?>, OwnerStats> ownerStats,
            HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap
    ) {
    }
}
