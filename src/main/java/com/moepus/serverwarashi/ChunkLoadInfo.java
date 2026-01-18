package com.moepus.serverwarashi;

import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.mixin.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.mixin.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.mixin.ServerLevelAcessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkLoadInfo {
    public int blockEntityCount;
    public int entityCount;

    public static void dumpToCsv(@NotNull HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap) {
        Path logDir = Paths.get("chunk_load");
        var timestamp = LocalDateTime.now().toString().replace(":", "-").replace(".", "-");
        var fileName = "chunk_load_info_" + timestamp + ".csv";
        Path csvPath = logDir.resolve(fileName);

        try {
            Files.createDirectories(logDir);

            try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
                writer.write("chunk_x,chunk_z,block_x,block_y,block_z,blockEntityCount,entityCount");
                writer.newLine();

                for (Map.Entry<Long, ChunkLoadInfo> entry : chunkLoadInfoMap.entrySet()) {
                    long chunkLong = entry.getKey();
                    ChunkLoadInfo info = entry.getValue();

                    ChunkPos chunkPos = new ChunkPos(chunkLong);
                    BlockPos blockPos = chunkPos.getWorldPosition();

                    writer.write(chunkPos.x + "," + chunkPos.z + "," + blockPos.getX() + "," + 0 + "," + blockPos.getZ() + "," + info.blockEntityCount + "," + info.entityCount);
                    writer.newLine();
                }
            }

        } catch (IOException ignored) {
        }
    }

    public static MutableComponent dumpTickets(ServerLevel level, boolean currentWorking, boolean saveCsv) {
        MutableComponent message = Component.empty().append("Dumped tickets:\n");
        ChunkMap chunkMap = level.getChunkSource().chunkMap;

        HashMap<TicketOwner<?>, Set<Long>> ownerMap = new HashMap<>();

        for (Long2ObjectMap.Entry<ChunkHolder> entry : chunkMap.visibleChunkMap.long2ObjectEntrySet()) {
            long chunkPos = entry.getLongKey();
            DistanceManagerAccessor distanceManager = (DistanceManagerAccessor) chunkMap.getDistanceManager();
            var tickets = distanceManager.getTickets().get(chunkPos);
            if (tickets == null || tickets.isEmpty()) continue;
            Ticket<?> ticket = GetTicket(tickets, currentWorking);
            if (ticket == null) continue;

            TicketOwner<?> owner = new TicketOwner<>(ticket, level);
            ownerMap.computeIfAbsent(owner, k -> new HashSet<>()).add(chunkPos);
        }

        PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAcessor) level).getEntityManager();
        PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
        EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
        EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;

        HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();

        for (var entry : ownerMap.entrySet()) {
            TicketOwner<?> owner = entry.getKey();
            Set<Long> chunks = entry.getValue();
            message.append(owner.asComponent());
            message.append(" C:" + chunks.size() + ",");

            // Block Entity count
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
            message.append(" BE: " + totalBlockEntities + ",");

            // Entity count
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
            message.append(" E: " + totalEntities + ".\n");
        }
        if (ownerMap.isEmpty()) {
            message.append("No tickets found\n");
        }
        if (saveCsv && !chunkLoadInfoMap.isEmpty()) {
            ChunkLoadInfo.dumpToCsv(chunkLoadInfoMap);
        }
        return message;
    }


    private static Ticket<?> GetTicket(SortedArraySet<Ticket<?>> tickets, boolean currentWorking) {
        for (Ticket<?> ticket : tickets) {
            // if (ticket.getType() == TicketType.PLAYER) continue;
            // if (ticket.getType() == TicketType.START) continue;

            int level;
            if (currentWorking) {
                level = ticket.getTicketLevel();
            } else {
                IPauseableTicket pauseable = (IPauseableTicket) (Object) ticket;
                level = pauseable.serverWarashi$getLevel();
            }
            if (level > 33) continue;
            return ticket;
        }

        return null;
    }

}
