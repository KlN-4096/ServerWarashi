package com.moepus.serverwarashi.chunkperf.core.session;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 性能会话的状态数据，仅保存采样过程中的累计值。
 * 会话逻辑由 {@link ChunkPerfSessionController} 负责。
 */
final class ChunkPerfSession {
    final ResourceKey<Level> dimension;
    final int groupIndex;
    final String ownerLabel;
    final LongOpenHashSet targetChunks;
    final int blockEntityCount;
    final int entityCount;
    final long startedAtNanos;
    final Object2LongOpenHashMap<String> typeTotals = new Object2LongOpenHashMap<>();
    final Object2LongOpenHashMap<String> typeCounts = new Object2LongOpenHashMap<>();
    final Object2LongOpenHashMap<String> entityTotals = new Object2LongOpenHashMap<>();
    final Object2LongOpenHashMap<String> entityCounts = new Object2LongOpenHashMap<>();
    long serverTickCount;
    long beTotalNanos;
    long beMaxNanos;
    long beTickCount;
    long entityTotalNanos;
    long entityMaxNanos;
    long entityTickCount;
    long chunkTotalNanos;
    long chunkMaxNanos;
    long chunkTickCount;

    ChunkPerfSession(ResourceKey<Level> dimension,
                     int groupIndex,
                     LongOpenHashSet targetChunks,
                     String ownerLabel,
                     int blockEntityCount,
                     int entityCount) {
        this.dimension = dimension;
        this.groupIndex = groupIndex;
        this.targetChunks = targetChunks;
        this.ownerLabel = ownerLabel;
        this.blockEntityCount = blockEntityCount;
        this.entityCount = entityCount;
        this.startedAtNanos = System.nanoTime();
    }
}
