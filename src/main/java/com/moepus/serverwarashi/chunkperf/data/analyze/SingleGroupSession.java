package com.moepus.serverwarashi.chunkperf.data.analyze;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能会话的状态数据，仅保存采样过程中的累计值。
 * 单分组性能会话数据。
 * 仅保存采样过程中的累计值，不包含控制逻辑。
 */
public final class SingleGroupSession extends GroupSession {
    // 稳定分组索引（对应命令里的 group 参数）。
    public final int groupIndex;
    // 目标分组标签（用于报告展示）。
    public final String ownerLabel;
    // 该会话要跟踪的区块集合（ChunkPos#asLong）。
    public final LongOpenHashSet targetChunks;
    // 快照中的方块实体数量（用于报告上下文）。
    public final int blockEntityCount;
    // 快照中的实体数量（用于报告上下文）。
    public final int entityCount;

    // 方块实体类型累计耗时（纳秒）。
    public final Object2LongOpenHashMap<String> typeTotals = new Object2LongOpenHashMap<>();
    // 实体类型累计耗时（纳秒）。
    public final Object2LongOpenHashMap<String> entityTotals = new Object2LongOpenHashMap<>();

    // 最近一次访问区块缓存：区块位置（ChunkPos#asLong）。
    public long lastChunkPos = Long.MIN_VALUE;
    // 最近一次访问区块是否属于 targetChunks。
    public boolean lastChunkTracked;

    // 挂起的实体耗时段：当前聚合区块（ChunkPos#asLong）。
    public long pendingEntityChunkPos = Long.MIN_VALUE;
    // 挂起的实体耗时段：累计耗时（纳秒）。
    public long pendingEntityNanos;
    // 挂起的方块实体耗时段：当前聚合区块（ChunkPos#asLong）。
    public long pendingBlockEntityChunkPos = Long.MIN_VALUE;
    // 挂起的方块实体耗时段：累计耗时（纳秒）。
    public long pendingBlockEntityNanos;

    // 方块实体总耗时（纳秒）。
    public long beTotalNanos;
    // 单次方块实体最大耗时（纳秒）。
    public long beMaxNanos;

    // 实体总耗时（纳秒）。
    public long entityTotalNanos;
    // 单次实体最大耗时（纳秒）。
    public long entityMaxNanos;

    // 区块总耗时（纳秒）。
    public long chunkTotalNanos;
    // 单次区块最大耗时（纳秒）。
    public long chunkMaxNanos;
    // 各区块累计耗时（纳秒）。
    public final Long2LongOpenHashMap chunkTotals = new Long2LongOpenHashMap();
    // 每个方块实体实例的最大单次耗时（纳秒）。
    public final Object2LongOpenHashMap<String> blockEntitySpikeNanos = new Object2LongOpenHashMap<>();
    // 方块实体实例展示标签。
    public final Map<String, String> blockEntitySpikeLabels = new HashMap<>();
    // 每个实体实例的最大单次耗时（纳秒）。
    public final Object2LongOpenHashMap<String> entitySpikeNanos = new Object2LongOpenHashMap<>();
    // 实体实例展示标签。
    public final Map<String, String> entitySpikeLabels = new HashMap<>();

    /**
     * 创建单分组会话数据。
     *
     * @param id               全服唯一会话 ID
     * @param dimension        会话维度
     * @param groupIndex       稳定分组索引
     * @param targetChunks     目标区块集合（ChunkPos#asLong）
     * @param ownerLabel       分组标签（报告显示）
     * @param blockEntityCount 快照中的方块实体数量
     * @param entityCount      快照中的实体数量
     */
    public SingleGroupSession(long id,
                              ResourceKey<Level> dimension,
                              int groupIndex,
                              LongOpenHashSet targetChunks,
                              String ownerLabel,
                              int blockEntityCount,
                              int entityCount) {
        super(id, dimension);
        this.groupIndex = groupIndex;
        this.targetChunks = targetChunks;
        this.ownerLabel = ownerLabel;
        this.blockEntityCount = blockEntityCount;
        this.entityCount = entityCount;
    }
}
