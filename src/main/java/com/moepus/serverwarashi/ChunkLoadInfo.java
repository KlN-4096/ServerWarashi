package com.moepus.serverwarashi;

import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.mixin.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.mixin.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.mixin.ServerLevelAcessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        return buildOwnerStatsComponent(
                level,
                currentWorking,
                TicketFilter.NON_SYSTEM,
                false,
                false,
                "Dumped tickets:",
                true,
                false,
                saveCsv
        );
    }

    /**
     * Builds an owner stats component with optional filters and CSV output.
     *
     * @param level           the server level to inspect
     * @param currentWorking  whether to use the current ticket level
     * @param filter          ticket filter to apply
     * @param excludeLevel33  whether to exclude tickets already at level 33
     * @param onlyLevel33     whether to include only tickets at level 33
     * @param header          header text
     * @param includeLower    whether to include lower buttons
     * @param includeRestore  whether to include restore buttons
     * @param saveCsv         whether to save CSV output
     * @return formatted component
     */
    public static MutableComponent buildOwnerStatsComponent(
            ServerLevel level,
            boolean currentWorking,
            TicketFilter filter,
            boolean excludeLevel33,
            boolean onlyLevel33,
            String header,
            boolean includeLower,
            boolean includeRestore,
            boolean saveCsv
    ) {
        HashMap<TicketOwner<?>, Set<Long>> ownerMap =
                collectTicketOwners(level, currentWorking, filter, excludeLevel33, onlyLevel33);

        PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAcessor) level).getEntityManager();
        PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
        EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
        EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;

        HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();
        HashMap<TicketOwner<?>, OwnerStats> ownerStatsMap =
                collectOwnerStats(level, ownerMap, sectionAccessor, sectionStorage, chunkLoadInfoMap);

        MutableComponent message = formatOwnerStatsToComponent(header, ownerStatsMap, includeLower, includeRestore);
        if (ownerMap.isEmpty()) {
            message.append(Component.literal("No tickets found\n"));
        }
        if (saveCsv && !chunkLoadInfoMap.isEmpty()) {
            ChunkLoadInfo.dumpToCsv(chunkLoadInfoMap);
        }
        return message;
    }

    /**
     * Collects ticket owners and their chunk positions for the given level.
     *
     * @param level          the server level to inspect
     * @param currentWorking whether to use the current ticket level
     * @return map of ticket owner to chunk positions
     */
    public static HashMap<TicketOwner<?>, Set<Long>> collectTicketOwners(ServerLevel level, boolean currentWorking) {
        return collectTicketOwners(level, currentWorking, TicketFilter.NON_SYSTEM);
    }

    /**
     * Collects ticket owners and their chunk positions for the given level.
     *
     * @param level          the server level to inspect
     * @param currentWorking whether to use the current ticket level
     * @param filter         ticket filter for system/non-system tickets
     * @return map of ticket owner to chunk positions
     */
    public static HashMap<TicketOwner<?>, Set<Long>> collectTicketOwners(
            ServerLevel level,
            boolean currentWorking,
            TicketFilter filter
    ) {
        return collectTicketOwners(level, currentWorking, filter, false, false);
    }

    /**
     * Collects ticket owners and their chunk positions for the given level.
     *
     * @param level            the server level to inspect
     * @param currentWorking   whether to use the current ticket level
     * @param filter           ticket filter for system/non-system tickets
     * @param excludeLevel33   whether to exclude tickets already at level 33
     * @return map of ticket owner to chunk positions
     */
    public static HashMap<TicketOwner<?>, Set<Long>> collectTicketOwners(
            ServerLevel level,
            boolean currentWorking,
            TicketFilter filter,
            boolean excludeLevel33,
            boolean onlyLevel33
    ) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        HashMap<TicketOwner<?>, Set<Long>> ownerMap = new HashMap<>();

        for (Long2ObjectMap.Entry<ChunkHolder> entry : chunkMap.visibleChunkMap.long2ObjectEntrySet()) {
            long chunkPos = entry.getLongKey();
            DistanceManagerAccessor distanceManager = (DistanceManagerAccessor) chunkMap.getDistanceManager();
            var tickets = distanceManager.getTickets().get(chunkPos);
            if (tickets == null || tickets.isEmpty()) continue;
            Ticket<?> ticket = GetTicket(tickets, currentWorking);
            if (ticket == null) continue;
            if (!filter.accept(ticket)) continue;
            if (currentWorking) {
                int levelValue = ticket.getTicketLevel();
                if (excludeLevel33 && levelValue == 33) continue;
                if (onlyLevel33 && levelValue != 33) continue;
            }

            TicketOwner<?> owner = new TicketOwner<>(ticket, level);
            ownerMap.computeIfAbsent(owner, k -> new HashSet<>()).add(chunkPos);
        }

        return ownerMap;
    }

    /**
     * Collects owner stats (chunk count, block entities, entities) for the given level.
     *
     * @param level              the server level to inspect
     * @param currentWorking     whether to use the current ticket level
     * @param ownerMap           owner map built by {@link #collectTicketOwners(ServerLevel, boolean, TicketFilter)}
     * @param sectionAccessor    entity section accessor
     * @param sectionStorage     entity section storage
     * @param chunkLoadInfoMap   chunk info output map
     * @return map of ticket owner to stats
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

            result.put(owner, new OwnerStats(chunks.size(), totalBlockEntities, totalEntities));
        }

        return result;
    }

    /**
     * Sorts owner stats by block entity count (descending), then by owner label.
     *
     * @param ownerStats owner stats map
     * @return sorted entry list
     */
    public static List<Map.Entry<TicketOwner<?>, OwnerStats>> sortOwnerStats(
            HashMap<TicketOwner<?>, OwnerStats> ownerStats
    ) {
        List<Map.Entry<TicketOwner<?>, OwnerStats>> entries = new ArrayList<>(ownerStats.entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<TicketOwner<?>, OwnerStats> e) -> e.getValue().blockEntityCount)
                .reversed()
                .thenComparing(e -> e.getKey().toString()));
        return entries;
    }

    /**
     * Builds a formatted component for owner stats.
     *
     * @param header      header text
     * @param ownerStats  owner stats map
     * @param includeLower whether to include the lower button
     * @return formatted component
     */
    public static MutableComponent formatOwnerStatsToComponent(
            String header,
            HashMap<TicketOwner<?>, OwnerStats> ownerStats,
            boolean includeLower,
            boolean includeRestore
    ) {
        MutableComponent root = Component.literal(header + "\n").withStyle(ChatFormatting.AQUA);
        List<Map.Entry<TicketOwner<?>, OwnerStats>> entries = sortOwnerStats(ownerStats);
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<TicketOwner<?>, OwnerStats> entry = entries.get(i);
            TicketOwner<?> owner = entry.getKey();
            OwnerStats stats = entry.getValue();
            String ownerName = owner.getName();
            String displayName = shortenBlockName(ownerName);
            String hoverText = displayName.equals(ownerName) ? null : ownerName;
            MutableComponent line = Component.empty()
                    .append(owner.asComponentWithName(displayName, hoverText))
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("G" + i + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("C=" + stats.chunkCount + " ").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal("BE=" + stats.blockEntityCount + " ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("E=" + stats.entityCount + " ").withStyle(ChatFormatting.YELLOW));
            if (includeLower) {
                line = line.append(Component.literal(" "))
                        .append(Component.literal("[analyze]")
                                .withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.LIGHT_PURPLE)
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Run a 60s analysis and auto-report")
                                        ))
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND,
                                                "/warashi perf start " + i + " 60"
                                        ))))
                        .append(Component.literal(" "))
                        .append(Component.literal("[lower]")
                                .withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.RED)
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Lower ticket level for this group")
                                        ))
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND,
                                                "/warashi perf lower " + i
                                        ))));
            }
            if (includeRestore) {
                line = line.append(Component.literal(" "))
                        .append(Component.literal("[restore]")
                                .withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.GREEN)
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Restore ticket level for this group")
                                        ))
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND,
                                                "/warashi perf restore " + i
                                        ))));
            }
            line = line.append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
            root = root.append(line);
        }
        return root;
    }

    /**
     * Shortens block display names by removing the namespace.
     *
     * @param name original name
     * @return shortened display name
     */
    private static String shortenBlockName(String name) {
        if (name == null) {
            return "";
        }
        if (name.startsWith("Block{") && name.endsWith("}")) {
            String inner = name.substring(6, name.length() - 1);
            int colon = inner.indexOf(':');
            if (colon >= 0 && colon + 1 < inner.length()) {
                return "Block{" + inner.substring(colon + 1) + "}";
            }
        }
        return name;
    }

    /**
     * Owner stats data holder.
     */
    public static final class OwnerStats {
        public final int chunkCount;
        public final int blockEntityCount;
        public final int entityCount;

        /**
         * Creates a new owner stats instance.
         *
         * @param chunkCount       chunk count
         * @param blockEntityCount block entity count
         * @param entityCount      entity count
         */
        public OwnerStats(int chunkCount, int blockEntityCount, int entityCount) {
            this.chunkCount = chunkCount;
            this.blockEntityCount = blockEntityCount;
            this.entityCount = entityCount;
        }
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
