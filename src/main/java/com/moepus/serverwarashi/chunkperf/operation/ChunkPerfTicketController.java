package com.moepus.serverwarashi.chunkperf.operation;

import com.moepus.serverwarashi.IPauseableTicket;
import com.moepus.serverwarashi.ManualPauseData;
import com.moepus.serverwarashi.TicketManager;
import com.moepus.serverwarashi.chunkperf.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupView;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * 票据暂停/恢复操作（面向分组）。
 *
 * @param groupView 用于解析分组索引并获取目标区块集合
 */
public record ChunkPerfTicketController(ChunkPerfGroupView groupView) {
    /**
     * 暂停（降低）指定分组的票据。
     */
    public Component lower(ServerLevel level, int groupIndex) {
        ChunkPerfGroupView.GroupLookup lookup = groupView.resolveGroup(
                level,
                groupIndex,
                ChunkPerfSnapshot.PauseMode.ACTIVE_ONLY
        );
        if (lookup.error() != null) {
            return lookup.error();
        }
        ChunkPerfSnapshot.ChunkPerfGroupEntry entry = lookup.entry();
        int changed = updatePausedState(level, entry.chunks(), true, false);
        return ChunkPerfMessages.loweredTickets(groupIndex, entry.label(), entry.stats().chunkCount(), changed);
    }

    /**
     * 恢复指定分组的票据。
     */
    public Component restore(ServerLevel level, int groupIndex) {
        ChunkPerfGroupView.GroupLookup lookup = groupView.resolveGroup(
                level,
                groupIndex,
                ChunkPerfSnapshot.PauseMode.PAUSED_ONLY
        );
        if (lookup.error() != null) {
            return lookup.error();
        }
        ChunkPerfSnapshot.ChunkPerfGroupEntry entry = lookup.entry();
        int changed = updatePausedState(level, entry.chunks(), false, true);
        return ChunkPerfMessages.restoredTickets(groupIndex, entry.label(), entry.stats().chunkCount(), changed);
    }

    /**
     * 对目标区块集合的非系统票据设置暂停状态，并刷新区块 ticket level。
     */
    private int updatePausedState(ServerLevel level,
                                  Set<Long> chunks,
                                  boolean paused,
                                  boolean isDecreasing) {
        ManualPauseData manualData = ManualPauseData.get(level);
        for (long chunkPos : chunks) {
            if (paused) {
                manualData.add(chunkPos);
            } else {
                manualData.remove(chunkPos);
            }
        }
        return TicketManager.applyPauseReasonToChunks(
                level,
                chunks,
                paused,
                IPauseableTicket.PAUSE_REASON_MANUAL,
                isDecreasing
        );
    }
}
