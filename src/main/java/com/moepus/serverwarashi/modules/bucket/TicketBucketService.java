package com.moepus.serverwarashi.modules.bucket;

import com.moepus.serverwarashi.common.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.ticket.TicketPauseService;
import com.moepus.serverwarashi.common.ticket.TicketUtils;
import com.moepus.serverwarashi.config.TicketBucketConfig;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.modules.pause.ManualPauseData;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自动分桶策略实现。
 */
public final class TicketBucketService {
    /**
     * 在一次维度 tick 中执行自动分桶逻辑。
     *
     * @param level 目标维度
     * @param age 当前全局 tick 计数
     */
    public void processTickets(ServerLevel level, int age) {
        if (!TicketBucketConfig.enabled()||age % TicketBucketConfig.runEvery() != 0) {
            return;
        }
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        var tickets = collectTickets(TicketUtils.getTickets(distanceManager).long2ObjectEntrySet(), ManualPauseData.get(level));
        if (tickets.isEmpty()) {
            return;
        }

        LongOpenHashSet modifiedChunks = TicketBucketConfig.pauseAll() ? pauseAllTickets(tickets) : bucketTickets(tickets, age);
        if (!modifiedChunks.isEmpty()) {
            TicketPauseService.updateChunkLevel(distanceManager, modifiedChunks);
        }
    }

    /**
     * 收集参与自动分桶的 ticket。
     * 若 chunk 内含有任意系统 ticket（如 PLAYER），整 chunk 跳过分桶。
     * vanilla 会在玩家 viewDistance 范围内每个 chunk 都注册一个 PLAYER ticket
     * 实例（level=31），借此天然覆盖玩家可见范围，避免削掉这些 chunk 的实体 tick。
     *
     * @param tickets 当前维度全部 ticket 入口
     * @param manualData 手动暂停数据
     * @return 可参与自动分桶的 ticket 列表
     */
    private List<TicketEntry> collectTickets(
            Iterable<? extends Long2ObjectMap.Entry<? extends Iterable<Ticket<?>>>> tickets,
            ManualPauseData manualData
    ) {
        List<TicketEntry> allTickets = new ArrayList<>();
        for (var entry : tickets) {
            long chunkPosLong = entry.getLongKey();
            if (manualData.contains(chunkPosLong)) {
                continue;
            }
            if (containsSystemTicket(entry.getValue())) {
                continue;
            }
            int chunkX = ChunkPos.getX(chunkPosLong);
            int chunkZ = ChunkPos.getZ(chunkPosLong);
            long morton = TicketMorton.morton2D(chunkX, chunkZ);

            for (Ticket<?> ticket : entry.getValue()) {
                allTickets.add(new TicketEntry(ticket, morton, chunkPosLong));
            }
        }
        return allTickets;
    }

    private static boolean containsSystemTicket(Iterable<? extends Ticket<?>> tickets) {
        for (Ticket<?> ticket : tickets) {
            if (TicketUtils.isSystemTicket(ticket)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将收集到的所有 ticket 一次性打上 AUTO 暂停原因。
     *
     * @param allTickets 全部候选 ticket
     * @return 被修改的区块映射
     */
    private LongOpenHashSet pauseAllTickets(List<TicketEntry> allTickets) {
        LongOpenHashSet modifiedChunks = new LongOpenHashSet();
        for (TicketEntry entry : allTickets) {
            if (TicketPauseService.updateTicketPauseReason(entry.ticket, true, IPauseableTicket.PAUSE_REASON_AUTO)) {
                modifiedChunks.add(entry.chunkPos);
            }
        }
        return modifiedChunks;
    }

    /**
     * 按当前 tick 周期选择一个活动桶，其余桶叠加 AUTO 暂停原因。
     *
     * @param allTickets 全部候选 ticket
     * @param age 当前全局 tick 计数
     * @return 被修改的区块映射
     */
    private LongOpenHashSet bucketTickets(List<TicketEntry> allTickets, int age) {
        allTickets.sort(Comparator.comparingLong(te -> te.morton));
        List<List<TicketEntry>> buckets = TicketMorton.divideChunkBuckets(allTickets);
        int currentGroupIndex = (age / TicketBucketConfig.runEvery()) % buckets.size();

        LongOpenHashSet modifiedChunks = new LongOpenHashSet();
        for (int i = 0; i < buckets.size(); i++) {
            boolean isActive = i == currentGroupIndex;
            for (TicketEntry entry : buckets.get(i)) {
                if (TicketPauseService.updateTicketPauseReason(entry.ticket, !isActive, IPauseableTicket.PAUSE_REASON_AUTO)) {
                    modifiedChunks.add(entry.chunkPos);
                }
            }
        }
        return modifiedChunks;
    }

    /**
     * 自动分桶过程中使用的 ticket 条目。
     *
     * @param ticket 原始 ticket
     * @param morton 所在 chunk 的 Morton 编码
     * @param chunkPos 所在 chunk 坐标
     */
    record TicketEntry(Ticket<?> ticket, long morton, long chunkPos) {}
}
