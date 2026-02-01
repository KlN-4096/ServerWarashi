package com.moepus.serverwarashi.chunkperf.core.cache;

import com.moepus.serverwarashi.TicketOwner;

import java.util.Set;

/**
 * 票据分组条目：包含 owner 对应的区块集合与统计信息。
 */
public record ChunkPerfGroupEntry(
        TicketOwner<?> owner,
        Set<Long> chunks,
        int chunkCount,
        int blockEntityCount,
        int entityCount
) {
    /**
     * 用于展示的标签。
     */
    public String label() {
        return owner.toString();
    }
}
