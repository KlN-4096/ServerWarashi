package com.moepus.serverwarashi.modules.idlefreeze;

import com.moepus.serverwarashi.common.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.ticket.TicketOwner;
import com.moepus.serverwarashi.common.ticket.TicketPauseService;
import com.moepus.serverwarashi.config.IdleFreezeConfig;
import com.moepus.serverwarashi.common.group.ChunkGroupService;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动扫描和按区块自动解冻。
 */
public final class IdleFreezeService {
    private IdleFreezeService() {
    }

    /**
     * 重新应用当前维度中已经保存的空闲冻结暂停标记。
     *
     * @param level 目标维度
     */
    public static void reapplySavedIdlePause(ServerLevel level) {
        IdlePauseData pauseData = IdlePauseData.get(level);
        if (pauseData.isEmpty()) {
            return;
        }
        for (IdlePauseData.FrozenGroup group : pauseData.groups()) {
            TicketPauseService.applyPauseReasonToChunks(
                    level,
                    group.chunks(),
                    true,
                    IPauseableTicket.PAUSE_REASON_IDLE
            );
        }
    }

    /**
     * 对当前维度执行一次完整的空闲冻结初始扫描。
     *
     * @param level 目标维度
     */
    public static void runInitialScan(ServerLevel level) {
        int todayDay = currentEpochDay();
        IdleVisitData visitData = IdleVisitData.get(level);
        IdlePauseData pauseData = IdlePauseData.get(level);
        prepareInitialScan(level, visitData, todayDay);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups =
                ChunkGroupService.listGroups(level, ChunkGroupSnapshot.PauseMode.ALL);
        Map<TicketOwner<?>, ChunkGroupSnapshot.ChunkGroupEntry> frozenGroups =
                collectFrozenGroups(groups, pauseData);
        refreshExistingFrozenGroups(level, pauseData, visitData, todayDay, frozenGroups);
        freezeNewGroups(level, pauseData, visitData, todayDay, groups, frozenGroups);
    }

    private static void prepareInitialScan(ServerLevel level, IdleVisitData visitData, int todayDay) {
        reapplySavedIdlePause(level);
        visitData.pruneBefore(todayDay - IdleFreezeConfig.lastSeenRetentionDays());
    }

    private static Map<TicketOwner<?>, ChunkGroupSnapshot.ChunkGroupEntry> collectFrozenGroups(
            List<ChunkGroupSnapshot.ChunkGroupEntry> groups,
            IdlePauseData pauseData
    ) {
        Map<TicketOwner<?>, ChunkGroupSnapshot.ChunkGroupEntry> result = new HashMap<>();
        for (ChunkGroupSnapshot.ChunkGroupEntry group : groups) {
            if (pauseData.findGroupOverlapping(group.chunks()) != null) {
                result.put(group.owner(), group);
            }
        }
        return result;
    }

    private static void refreshExistingFrozenGroups(ServerLevel level,
                                                    IdlePauseData pauseData,
                                                    IdleVisitData visitData,
                                                    int todayDay,
                                                    Map<TicketOwner<?>, ChunkGroupSnapshot.ChunkGroupEntry> frozenGroups) {
        for (ChunkGroupSnapshot.ChunkGroupEntry group : frozenGroups.values()) {
            if (shouldIdleFreeze(group, visitData, todayDay)) {
                applyIdlePause(level, pauseData, group.chunks());
                continue;
            }
            clearIdlePause(level, pauseData, group.chunks());
        }
    }

    private static void freezeNewGroups(ServerLevel level,
                                        IdlePauseData pauseData,
                                        IdleVisitData visitData,
                                        int todayDay,
                                        List<ChunkGroupSnapshot.ChunkGroupEntry> groups,
                                        Map<TicketOwner<?>, ChunkGroupSnapshot.ChunkGroupEntry> frozenGroups) {
        for (ChunkGroupSnapshot.ChunkGroupEntry group : groups) {
            if (!frozenGroups.containsKey(group.owner()) && shouldIdleFreeze(group, visitData, todayDay)) {
                applyIdlePause(level, pauseData, group.chunks());
            }
        }
    }

    /**
     * 当玩家进入某个区块时，尝试解除其所在冻结分组的空闲暂停。
     *
     * @param level 目标维度
     * @param chunkPos 玩家进入的区块坐标
     */
    public static void tryUnfreezeByChunk(ServerLevel level, long chunkPos) {
        IdlePauseData pauseData = IdlePauseData.get(level);
        IdlePauseData.FrozenGroup frozenGroup = pauseData.removeGroupContaining(chunkPos);
        if (frozenGroup == null) {
            return;
        }
        clearIdlePause(level, pauseData, frozenGroup.chunks());
    }

    /**
     * 计算当前系统时区下的 epoch day。
     *
     * @return 当前日期对应的天数
     */
    public static int currentEpochDay() {
        return (int) LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    /**
     * 判断一个 ticket 分组是否满足空闲冻结条件。
     *
     * @param group 待判断的分组
     * @param visitData 最近访问记录
     * @param todayDay 当前日期
     * @return 若应被冻结则返回 {@code true}
     */
    private static boolean shouldIdleFreeze(ChunkGroupSnapshot.ChunkGroupEntry group,
                                            IdleVisitData visitData,
                                            int todayDay) {
        ChunkGroupSnapshot.OwnerStats stats = group.stats();
        int totalLoad = stats.blockEntityCount() + stats.entityCount();
        if (totalLoad <= IdleFreezeConfig.minGroupLoad()) {
            return false;
        }

        int latestSeenDay = Integer.MIN_VALUE;
        for (long chunkPos : group.chunks()) {
            latestSeenDay = Math.max(latestSeenDay, visitData.getLastSeenDay(chunkPos));
        }
        if (latestSeenDay == Integer.MIN_VALUE) {
            return true;
        }

        return todayDay - latestSeenDay > IdleFreezeConfig.inactiveDays();
    }

    /**
     * 将一组区块登记为 idle 冻结，并叠加 IDLE 暂停原因。
     *
     * @param level 目标维度
     * @param pauseData 冻结数据
     * @param chunks 目标区块集合
     */
    private static void applyIdlePause(ServerLevel level, IdlePauseData pauseData, Set<Long> chunks) {
        pauseData.addGroup(chunks);
        TicketPauseService.applyPauseReasonToChunks(
                level,
                chunks,
                true,
                IPauseableTicket.PAUSE_REASON_IDLE
        );
    }

    /**
     * 解除一组区块上的 idle 冻结记录和 IDLE 暂停原因。
     *
     * @param level 目标维度
     * @param pauseData 冻结数据
     * @param chunks 目标区块集合
     */
    private static void clearIdlePause(ServerLevel level, IdlePauseData pauseData, Set<Long> chunks) {
        IdlePauseData.FrozenGroup frozenGroup = pauseData.findGroupOverlapping(chunks);
        if (frozenGroup != null) {
            pauseData.removeGroup(frozenGroup);
        }
        TicketPauseService.applyPauseReasonToChunks(
                level,
                chunks,
                false,
                IPauseableTicket.PAUSE_REASON_IDLE
        );
    }
}
