package com.moepus.serverwarashi.modules.performance.report;

import com.moepus.serverwarashi.common.ticket.TicketOwner;
import com.moepus.serverwarashi.common.group.ChunkGroupService;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChunkPerf 分组展示视图。
 */
public final class TicketPerfGroupOutput {
    private TicketPerfGroupOutput() {
    }

    private static void dumpToCsv(@NotNull HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap) {
        Path logDir = Paths.get("chunk_load");
        String timestamp = LocalDateTime.now().toString().replace(":", "-").replace(".", "-");
        Path csvPath = logDir.resolve("chunk_load_info_" + timestamp + ".csv");
        try {
            Files.createDirectories(logDir);
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
                writer.write("chunk_x,chunk_z,block_x,block_y,block_z,blockEntityCount,entityCount");
                writer.newLine();
                for (Map.Entry<Long, ChunkGroupSnapshot.ChunkLoadInfo> entry : chunkLoadInfoMap.entrySet()) {
                    ChunkPos chunkPos = new ChunkPos(entry.getKey());
                    BlockPos blockPos = chunkPos.getWorldPosition();
                    ChunkGroupSnapshot.ChunkLoadInfo info = entry.getValue();
                    writer.write(chunkPos.x + "," + chunkPos.z + "," + blockPos.getX() + ",0," + blockPos.getZ() + ","
                            + info.blockEntityCount() + "," + info.entityCount());
                    writer.newLine();
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 列出分组并写入缓存快照（指定排序）。
     */
    public static Component listGroups(ServerLevel level,
                                       ChunkGroupSnapshot.PauseMode pauseMode,
                                       String header,
                                       ChunkGroupSnapshot.SortMode sortMode,
                                       boolean showActions,
                                       boolean saveCsv) {
        ChunkGroupService groupQueries = ChunkGroupService.instance();
        ChunkGroupSnapshot.SnapshotData snapshot = groupQueries.refreshSnapshot(level, pauseMode);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = snapshot.groups();
        Map<TicketOwner<?>, Integer> indexMap = groupQueries.indexMap(level, groups);
        if (saveCsv && !snapshot.chunkLoadInfoMap().isEmpty()) {
            dumpToCsv(snapshot.chunkLoadInfoMap());
        }
        return TicketPerfMessages.formatOwnerStatsToComponent(
                header,
                level.dimension(),
                groups,
                sortMode,
                pauseMode,
                showActions,
                indexMap
        );
    }
}
