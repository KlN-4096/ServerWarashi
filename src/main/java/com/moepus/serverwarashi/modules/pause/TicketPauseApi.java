package com.moepus.serverwarashi.modules.pause;

import com.moepus.serverwarashi.common.ticket.TicketOwner;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/**
 * ChunkPause 模块对外入口。
 * 层级：入口层。
 * 上游：Commands / ChunkGroup。下游：service / data。
 */
public final class TicketPauseApi {
    private TicketPauseApi() {
    }

    /**
     * 统计给定分组列表在当前维度上的暂停状态。
     *
     * @param level 目标维度
     * @param groups 待统计的分组列表
     * @return 以分组 owner 为键的暂停状态映射
     */
    public static Map<TicketOwner<?>, TicketPauseGroupState> collectPauseStates(
            ServerLevel level,
            List<ChunkGroupSnapshot.ChunkGroupEntry> groups
    ) {
        return TicketPauseGroupService.collectPauseStates(level, groups);
    }

    /**
     * 手动降低指定分组内的非系统票据。
     *
     * @param level 目标维度
     * @param groupIndex 分组编号
     * @return 操作结果消息
     */
    public static Component lower(ServerLevel level, int groupIndex) {
        return TicketPauseGroupService.lower(level, groupIndex);
    }

    /**
     * 恢复指定分组内被手动降低的票据。
     *
     * @param level 目标维度
     * @param groupIndex 分组编号
     * @return 操作结果消息
     */
    public static Component restore(ServerLevel level, int groupIndex) {
        return TicketPauseGroupService.restore(level, groupIndex);
    }

    /**
     * 恢复当前位置所在分组内被手动降低的票据。
     *
     * @param level 目标维度
     * @param pos 当前位置
     * @return 操作结果消息
     */
    public static Component restoreHere(ServerLevel level, BlockPos pos) {
        return TicketPauseGroupService.restoreHere(level, pos);
    }

    /**
     * 将持久化的手动暂停状态补写回当前维度中的 live tickets。
     *
     * @param level 目标维度
     */
    public static void replayPendingManualPause(ServerLevel level) {
        TicketPauseGroupService.replayPendingManualPause(level);
    }

    /**
     * 列出 lowered 分组。
     */
    public static Component listLoweredGroups(ServerLevel level,
                                              String header,
                                              TicketPauseGroupState.Reason reasonFilter) {
        return TicketPauseGroupService.listLoweredGroups(level, header, reasonFilter);
    }

    /**
     * 清除指定维度的自动暂停状态。
     *
     * @param level 目标维度
     */
    public static void clearAutoPause(ServerLevel level) {
        com.moepus.serverwarashi.common.ticket.TicketPauseService.clearAutoPause(level);
    }
}
