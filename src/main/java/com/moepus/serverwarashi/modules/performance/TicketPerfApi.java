package com.moepus.serverwarashi.modules.performance;

import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import com.moepus.serverwarashi.modules.performance.report.TicketPerfGroupOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * ChunkPerf 模块对外门面。
 * 层级：入口层。
 * 上游：Commands。下游：TicketPerfRuntime / TicketPerfGroupOutput。
 */
public final class TicketPerfApi {
    private TicketPerfApi() {
    }

    /**
     * 列出分组并写入缓存快照（指定排序）。
     */
    public static Component dumpTickets(ServerLevel level,
                                        ChunkGroupSnapshot.PauseMode pauseMode,
                                        boolean saveCsv) {
        return TicketPerfGroupOutput.listGroups(
                level,
                pauseMode,
                "Dumped tickets:",
                ChunkGroupSnapshot.SortMode.BLOCK_ENTITY,
                false,
                saveCsv
        );
    }

    public static Component listGroups(ServerLevel level,
                                       ChunkGroupSnapshot.PauseMode pauseMode,
                                       String header,
                                       ChunkGroupSnapshot.SortMode sortMode,
                                       boolean showActions,
                                       boolean saveCsv) {
        return TicketPerfGroupOutput.listGroups(level, pauseMode, header, sortMode, showActions, saveCsv);
    }

    public static Component start(ServerLevel sourceLevel,
                                  int groupIndex,
                                  int durationSec,
                                  UUID playerId) {
        return TicketPerfRuntime.start(sourceLevel, groupIndex, durationSec, playerId);
    }

    public static Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        return TicketPerfRuntime.startAll(level, durationSec, playerId);
    }

    public static Component stop(ServerLevel level) {
        return TicketPerfRuntime.stop(level);
    }
}
