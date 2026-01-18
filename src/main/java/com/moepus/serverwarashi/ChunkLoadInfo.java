package com.moepus.serverwarashi;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

                    writer.write(
                            chunkPos.x + "," +
                                    chunkPos.z + "," +
                                    blockPos.getX() + "," +
                                    0 + "," +
                                    blockPos.getZ() + "," +
                                    info.blockEntityCount + "," +
                                    info.entityCount
                    );
                    writer.newLine();
                }
            }

        } catch (IOException ignored) {
        }
    }
}
