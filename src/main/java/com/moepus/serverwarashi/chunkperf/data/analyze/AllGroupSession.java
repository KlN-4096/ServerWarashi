package com.moepus.serverwarashi.chunkperf.data.analyze;

import com.moepus.serverwarashi.chunkperf.data.ChunkPerfSnapshot;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 全分组会话数据。
 * 性能会话的状态数据，仅保存采样过程中的累计值。
 * 仅保存采样过程中的累计值，不包含控制逻辑。
 */
public final class AllGroupSession extends GroupSession {
    // 本次分析涉及的分组列表。
    // 后续所有按组数组（be/entity/chunk）都与该列表下标一一对应。
    public final List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups;
    // 区块到分组下标的映射：chunkPos(long) -> groupIndex(int)。
    public final Long2IntOpenHashMap chunkToGroupIndex;

    // 每个分组的方块实体总耗时（纳秒）。
    public final long[] beTotalNanos;
    // 每个分组的实体总耗时（纳秒）。
    public final long[] entityTotalNanos;
    // 每个分组的区块总耗时（纳秒）。
    public final long[] chunkTotalNanos;

    // 最近一次访问区块缓存：区块位置（ChunkPos#asLong）。
    public long lastChunkPos = Long.MIN_VALUE;
    // 最近一次访问区块缓存：所属分组下标（-1 表示不在任何分组）。
    public int lastGroupIndex = -1;

    // 挂起的实体耗时段：当前聚合分组下标（-1 表示无）。
    public int pendingEntityGroupIndex = -1;
    // 挂起的实体耗时段：累计耗时（纳秒）。
    public long pendingEntityNanos;

    // 挂起的方块实体耗时段：当前聚合分组下标（-1 表示无）。
    public int pendingBlockEntityGroupIndex = -1;
    // 挂起的方块实体耗时段：累计耗时（纳秒）。
    public long pendingBlockEntityNanos;

    // 创建全分组会话数据。
    // id: 全服唯一会话 ID
    // dimension: 会话维度
    // groups: 分组列表（数组统计与其下标对齐）
    // chunkToGroupIndex: 区块到分组下标映射
    public AllGroupSession(long id,
                           ResourceKey<Level> dimension,
                           List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups,
                           Long2IntOpenHashMap chunkToGroupIndex) {
        super(id, dimension);
        this.groups = groups;
        this.chunkToGroupIndex = chunkToGroupIndex;
        int size = groups.size();
        this.beTotalNanos = new long[size];
        this.entityTotalNanos = new long[size];
        this.chunkTotalNanos = new long[size];
    }
}
