package com.moepus.serverwarashi.common.group;

import com.moepus.serverwarashi.common.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.ticket.TicketOwner;
import com.moepus.serverwarashi.common.ticket.TicketUtils;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.mixin.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.mixin.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.mixin.ServerLevelAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chunk 分组快照采集器。
 * 负责从运行时状态中采集分组与统计快照。
 */
public final class ChunkGroupCollector {
    private ChunkGroupCollector() {
    }

    public static ChunkGroupSnapshot.SnapshotData collect(ServerLevel level,
                                                          ChunkGroupSnapshot.PauseMode pauseMode) {
        HashMap<TicketOwner<?>, Set<Long>> ownerMap = collectTicketOwners(level, pauseMode);
        if (ownerMap.isEmpty()) {
            return ChunkGroupSnapshot.EMPTY_SNAPSHOT;
        }
        PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAccessor) level).getEntityManager();
        PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
        EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
        EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;
        HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap =
                collectChunkLoadInfo(level, ownerMap, sectionAccessor, sectionStorage);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = buildGroupEntries(ownerMap, chunkLoadInfoMap);
        return new ChunkGroupSnapshot.SnapshotData(groups, chunkLoadInfoMap);
    }

    private static HashMap<TicketOwner<?>, Set<Long>> collectTicketOwners(ServerLevel level,
                                                                          ChunkGroupSnapshot.PauseMode pauseMode) {
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        HashMap<TicketOwner<?>, Set<Long>> ownerMap = new HashMap<>();

        for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry : distanceManager.getTickets().long2ObjectEntrySet()) {
            long chunkPos = entry.getLongKey();
            SortedArraySet<Ticket<?>> tickets = entry.getValue();
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            Ticket<?> ticket = selectTicket(tickets, pauseMode);
            if (ticket == null) {
                continue;
            }

            TicketOwner<?> owner = new TicketOwner<>(ticket, level);
            String ownerName = owner.getName();
            if ("unknown".equals(ownerName)
                    || "Unknown block".equals(ownerName)
                    || "Unknown entity".equals(ownerName)) {
                continue;
            }
            ownerMap.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
        }

        return ownerMap;
    }

    private static HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> collectChunkLoadInfo(
            ServerLevel level,
            HashMap<TicketOwner<?>, Set<Long>> ownerMap,
            EntitySectionStorageAccessor sectionAccessor,
            EntitySectionStorage<?> sectionStorage
    ) {
        HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();
        for (Set<Long> chunks : ownerMap.values()) {
            for (long chunkPos : chunks) {
                if (chunkLoadInfoMap.containsKey(chunkPos)) {
                    continue;
                }
                ChunkPos pos = new ChunkPos(chunkPos);
                int blockEntityCount = 0;
                ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    blockEntityCount = chunk.getBlockEntitiesPos().size();
                }
                int[] entityCount = {0};
                sectionAccessor.invokeGetChunkSections(pos.x, pos.z).forEach(sectionIndex -> {
                    EntitySection<?> section = sectionStorage.getSection(sectionIndex);
                    if (section != null) {
                        entityCount[0] += section.size();
                    }
                });
                chunkLoadInfoMap.put(chunkPos, new ChunkGroupSnapshot.ChunkLoadInfo(blockEntityCount, entityCount[0]));
            }
        }
        return chunkLoadInfoMap;
    }

    private static List<ChunkGroupSnapshot.ChunkGroupEntry> buildGroupEntries(
            HashMap<TicketOwner<?>, Set<Long>> ownerMap,
            HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap
    ) {
        List<ChunkGroupSnapshot.ChunkGroupEntry> entries = new ArrayList<>(ownerMap.size());
        for (var entry : ownerMap.entrySet()) {
            int totalBlockEntities = 0;
            int totalEntities = 0;
            for (long chunkPos : entry.getValue()) {
                ChunkGroupSnapshot.ChunkLoadInfo info = chunkLoadInfoMap.get(chunkPos);
                if (info != null) {
                    totalBlockEntities += info.blockEntityCount();
                    totalEntities += info.entityCount();
                }
            }
            entries.add(new ChunkGroupSnapshot.ChunkGroupEntry(
                    entry.getKey(),
                    entry.getValue(),
                    new ChunkGroupSnapshot.OwnerStats(entry.getValue().size(), totalBlockEntities, totalEntities)
            ));
        }
        entries.sort(Comparator
                .comparingInt((ChunkGroupSnapshot.ChunkGroupEntry e) -> e.stats().blockEntityCount())
                .reversed()
                .thenComparing(e -> e.owner().toString()));
        return entries;
    }

    private static Ticket<?> selectTicket(SortedArraySet<Ticket<?>> tickets,
                                          ChunkGroupSnapshot.PauseMode pauseMode) {
        Ticket<?> fallback = null;
        for (Ticket<?> ticket : tickets) {
            boolean paused = ((IPauseableTicket) (Object) ticket).serverWarashi$isPaused();
            if (!pauseMode.accept(paused)) {
                continue;
            }
            if (TicketUtils.getOriginLevel(ticket)>TicketUtils.MAX_PAUSEABLE_LEVEL) {
                continue;
            }
            if (!TicketUtils.isSystemTicket(ticket)) {
                return ticket;
            }
            if (fallback == null || systemTicketPriority(ticket) < systemTicketPriority(fallback)) {
                fallback = ticket;
            }
        }
        return fallback;
    }

    /**
     * ticket优先级,用于区分BE和E归属
     */
    private static int systemTicketPriority(Ticket<?> ticket) {
        var type = ticket.getType();
        if (type == net.minecraft.server.level.TicketType.FORCED) {
            return 0;
        }
        return 255;
    }
}
