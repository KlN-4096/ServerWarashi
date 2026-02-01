package com.moepus.serverwarashi.chunkperf.core.session;

import com.moepus.serverwarashi.chunkperf.core.ChunkPerfMessages;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupCache;
import com.moepus.serverwarashi.chunkperf.core.cache.ChunkPerfGroupEntry;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

    /**
     * 性能会话管理：启动/停止会话、采样记录与自动分析。
     */
public final class ChunkPerfSessionController {
    private final ChunkPerfGroupCache groupCache;
    /**
     * 按维度维护的会话数据。
     */
    private final Map<ResourceKey<Level>, ChunkPerfSession> sessions = new HashMap<>();
    /**
     * 按维度维护的自动分析信息。
     */
    private final Map<ResourceKey<Level>, AutoAnalysis> autoAnalyses = new HashMap<>();

    /**
     * 创建会话控制器。
     *
     * @param groupCache 分组缓存与解析器
     */
    public ChunkPerfSessionController(ChunkPerfGroupCache groupCache) {
        this.groupCache = groupCache;
    }

    /**
     * 启动性能会话（按分组索引解析）。
     *
     * @param sourceLevel 目标世界
     * @param groupIndex  分组索引
     * @param durationSec 持续秒数，{@code 0} 表示不启用定时分析
     * @param playerId    触发玩家 ID，可能为 {@code null}
     * @return 启动结果消息
     */
    public Component start(ServerLevel sourceLevel,
                           int groupIndex,
                           int durationSec,
                           UUID playerId) {
        ChunkPerfGroupCache.GroupLookup lookup = groupCache.resolveGroup(
                sourceLevel,
                groupIndex,
                ChunkPerfTickets.PauseMode.ACTIVE_ONLY,
                ChunkPerfMessages.noTicketGroupsFound()
        );
        if (lookup.hasError()) {
            return lookup.error();
        }
        ChunkPerfGroupEntry entry = lookup.entry();
        LongOpenHashSet targetChunks = new LongOpenHashSet(entry.chunks());
        ChunkPerfSession session = new ChunkPerfSession(
                sourceLevel.dimension(),
                groupIndex,
                targetChunks,
                entry.label(),
                entry.blockEntityCount(),
                entry.entityCount()
        );
        sessions.put(sourceLevel.dimension(), session);
        if (durationSec > 0) {
            autoAnalyses.put(sourceLevel.dimension(),
                    new AutoAnalysis(System.currentTimeMillis() + durationSec * 1000L, playerId));
            return ChunkPerfMessages.sessionStarted(
                    entry.owner().asComponent(),
                    groupIndex,
                    entry.chunkCount(),
                    entry.blockEntityCount(),
                    entry.entityCount(),
                    durationSec
            );
        }
        autoAnalyses.remove(sourceLevel.dimension());
        return ChunkPerfMessages.sessionStarted(
                entry.owner().asComponent(),
                groupIndex,
                entry.chunkCount(),
                entry.blockEntityCount(),
                entry.entityCount(),
                0
        );
    }

    /**
     * 停止会话并生成报告。
     *
     * @param level 目标世界
     * @return 报告消息或无会话提示
     */
    public Component stop(ServerLevel level) {
        ChunkPerfSession session = sessions.remove(level.dimension());
        if (session == null) {
            return ChunkPerfMessages.noActiveSession();
        }
        autoAnalyses.remove(level.dimension());
        return buildReport(session);
    }

    /**
     * 推进会话并处理定时分析。
     *
     * @param server 服务器实例
     */
    public void tickSessions(MinecraftServer server) {
        for (var session : sessions.values()) {
            session.serverTickCount++;
        }
        if (autoAnalyses.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (var entry : new ArrayList<>(autoAnalyses.entrySet())) {
            if (now < entry.getValue().endAtMillis) {
                continue;
            }
            ResourceKey<Level> dim = entry.getKey();
            ServerLevel level = server.getLevel(dim);
            if (level == null) {
                autoAnalyses.remove(dim);
                continue;
            }
            Component report = stop(level);
            if (entry.getValue().playerId != null) {
                var player = server.getPlayerList().getPlayer(entry.getValue().playerId);
                if (player != null) {
                    player.sendSystemMessage(report);
                } else {
                    server.sendSystemMessage(report);
                }
            } else {
                server.sendSystemMessage(report);
            }
        }
    }

    /**
     * 判断是否需要跟踪该位置。
     *
     * @param level 目标世界
     * @param pos   方块坐标
     * @return 是否需要采样
     */
    public boolean shouldTrack(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        ChunkPerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return false;
        }
        long chunkPos = ChunkPos.asLong(pos);
        return session.targetChunks.contains(chunkPos);
    }

    /**
     * 记录实体 Tick 数据（方块实体也作为实体处理）。
     *
     * @param level         目标世界
     * @param pos           方块坐标
     * @param type          实体类型标识
     * @param durationNanos 耗时（纳秒）
     * @param isBlockEntity 是否为方块实体
     */
    public void onEntityTick(Level level, BlockPos pos, String type, long durationNanos, boolean isBlockEntity) {
        ChunkPerfSession session = resolveSession(level, pos);
        if (session == null) {
            return;
        }
        if (isBlockEntity) {
            recordBlockEntity(session, type, durationNanos);
        } else {
            recordEntity(session, type, durationNanos);
        }
    }

    /**
     * 记录区块 Tick 数据。
     *
     * @param level         目标世界
     * @param pos           区块坐标
     * @param durationNanos 耗时（纳秒）
     */
    public void onChunkTick(Level level, ChunkPos pos, long durationNanos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkPerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return;
        }
        if (!session.targetChunks.contains(pos.toLong())) {
            return;
        }
        recordChunk(session, durationNanos);
    }

    /**
     * 根据方块坐标判断当前 Tick 是否需要被采样。
     * <p>
     * 这是 {@link #onEntityTick(Level, BlockPos, String, long, boolean)} 的过滤入口：
     * 只有当世界是 {@link ServerLevel}、存在会话、且该坐标所在区块属于会话的目标集合时，
     * 才返回会话并继续统计；否则返回 {@code null} 并跳过统计。
     *
     * @param level 目标世界
     * @param pos   方块坐标
     * @return 命中的会话，或 {@code null}
     */
    private ChunkPerfSession resolveSession(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (pos == null) {
            return null;
        }
        ChunkPerfSession session = sessions.get(serverLevel.dimension());
        if (session == null) {
            return null;
        }
        long chunkPos = ChunkPos.asLong(pos);
        if (!session.targetChunks.contains(chunkPos)) {
            return null;
        }
        return session;
    }

    /**
     * 记录方块实体耗时与统计。
     *
     * @param session       会话数据
     * @param type          方块实体类型标识
     * @param durationNanos 耗时（纳秒）
     */
    private void recordBlockEntity(ChunkPerfSession session, String type, long durationNanos) {
        session.beTickCount++;
        session.beTotalNanos += durationNanos;
        if (durationNanos > session.beMaxNanos) {
            session.beMaxNanos = durationNanos;
        }
        session.typeTotals.addTo(type, durationNanos);
        session.typeCounts.addTo(type, 1);
    }

    /**
     * 记录实体耗时与统计。
     *
     * @param session       会话数据
     * @param type          实体类型标识
     * @param durationNanos 耗时（纳秒）
     */
    private void recordEntity(ChunkPerfSession session, String type, long durationNanos) {
        session.entityTickCount++;
        session.entityTotalNanos += durationNanos;
        if (durationNanos > session.entityMaxNanos) {
            session.entityMaxNanos = durationNanos;
        }
        session.entityTotals.addTo(type, durationNanos);
        session.entityCounts.addTo(type, 1);
    }

    /**
     * 记录区块耗时与统计。
     *
     * @param session       会话数据
     * @param durationNanos 耗时（纳秒）
     */
    private void recordChunk(ChunkPerfSession session, long durationNanos) {
        session.chunkTickCount++;
        session.chunkTotalNanos += durationNanos;
        if (durationNanos > session.chunkMaxNanos) {
            session.chunkMaxNanos = durationNanos;
        }
    }

    /**
     * 构建会话报告消息。
     *
     * @param session 会话数据
     * @return 报告消息
     */
    private Component buildReport(ChunkPerfSession session) {
        long elapsedNanos = System.nanoTime() - session.startedAtNanos;
        return ChunkPerfMessages.buildReport(
                session.dimension,
                session.groupIndex,
                session.ownerLabel,
                session.targetChunks.size(),
                session.blockEntityCount,
                session.entityCount,
                session.serverTickCount,
                session.beTotalNanos,
                session.beMaxNanos,
                session.beTickCount,
                session.entityTotalNanos,
                session.entityMaxNanos,
                session.entityTickCount,
                session.chunkTotalNanos,
                session.chunkMaxNanos,
                session.chunkTickCount,
                session.typeTotals,
                session.typeCounts,
                session.entityTotals,
                session.entityCounts,
                Duration.ofNanos(elapsedNanos)
        );
    }

    /**
     * 定时分析元数据。
     *
     * @param endAtMillis 结束时间戳（毫秒）
     * @param playerId    触发玩家 ID，可为空
     */
    private record AutoAnalysis(long endAtMillis, UUID playerId) {
    }
}
