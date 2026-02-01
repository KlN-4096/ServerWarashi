package com.moepus.serverwarashi.chunkperf.ticket;

import com.moepus.serverwarashi.TicketManager;
import com.moepus.serverwarashi.chunkperf.core.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupCache;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupEntry;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

import java.util.Set;

/**
 * 票据暂停/恢复操作（面向分组）。
 *
 * @param groupCache 用于解析分组索引并获取目标区块集合
 */
public record ChunkPerfTicketController(ChunkPerfGroupCache groupCache) {

    /**
     * 暂停（降低）指定分组的票据。
     */
    public Component lower(ServerLevel level, int groupIndex) {
        ChunkPerfGroupCache.GroupLookup lookup = groupCache.resolveGroup(
                level,
                groupIndex,
                ChunkPerfTickets.PauseMode.ACTIVE_ONLY,
                ChunkPerfMessages.noTicketGroupsFound()
        );
        if (lookup.hasError()) {
            return lookup.error();
        }
        ChunkPerfGroupEntry entry = lookup.entry();
        int changed = updatePausedState(level, entry.chunks(), true, false);
        return ChunkPerfMessages.loweredTickets(groupIndex, entry.label(), entry.chunkCount(), changed);
    }

    /**
     * 恢复指定分组的票据。
     */
    public Component restore(ServerLevel level, int groupIndex) {
        ChunkPerfGroupCache.GroupLookup lookup = groupCache.resolveGroup(
                level,
                groupIndex,
                ChunkPerfTickets.PauseMode.PAUSED_ONLY,
                ChunkPerfMessages.noLoweredTicketGroupsFound()
        );
        if (lookup.hasError()) {
            return lookup.error();
        }
        ChunkPerfGroupEntry entry = lookup.entry();
        int changed = updatePausedState(level, entry.chunks(), false, true);
        return ChunkPerfMessages.restoredTickets(groupIndex, entry.label(), entry.chunkCount(), changed);
    }

    /**
     * 对目标区块集合的非系统票据设置暂停状态，并刷新区块 ticket level。
     */
    private int updatePausedState(ServerLevel level,
                                  Set<Long> chunks,
                                  boolean paused,
                                  boolean isDecreasing) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        DistanceManagerAccessor accessor = (DistanceManagerAccessor) distanceManager;
        int updated = 0;
        for (long chunkPos : chunks) {
            SortedArraySet<Ticket<?>> ticketSet = accessor.getTickets().get(chunkPos);
            if (ticketSet == null || ticketSet.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Ticket<?> ticket : ticketSet) {
                if (TicketManager.isSystemTicket(ticket)) {
                    continue;
                }
                if (TicketManager.applyPause(ticket, paused)) {
                    changed = true;
                }
            }
            if (changed) {
                TicketManager.updateChunkLevel(accessor, chunkPos, isDecreasing);
                updated++;
            }
        }
        return updated;
    }
}
