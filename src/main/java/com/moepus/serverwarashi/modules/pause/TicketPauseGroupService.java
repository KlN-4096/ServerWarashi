package com.moepus.serverwarashi.modules.pause;

import com.moepus.serverwarashi.common.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.ticket.TicketOwner;
import com.moepus.serverwarashi.common.ticket.TicketPauseService;
import com.moepus.serverwarashi.common.ticket.TicketUtils;
import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import com.moepus.serverwarashi.common.group.ChunkGroupService;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分组级手动暂停操作服务。
 * 层级：业务层。
 * 上游：TicketPauseApi。下游：ChunkGroupApi / TicketPauseApi / ManualPauseData。
 */
public final class TicketPauseGroupService {
    private static final TicketPauseGroupState RUNNING = new TicketPauseGroupState(false, false, false);

    private TicketPauseGroupService() {
    }

    /**
     * 暂停（降低）指定分组的票据。
     */
    public static Component lower(ServerLevel level, int groupIndex) {
        ChunkGroupService.GroupLookup lookup = ChunkGroupService.resolve(level, groupIndex, ChunkGroupSnapshot.PauseMode.ALL);
        if (lookup.failure() != null) {
            return toLookupError(lookup.failure(), false);
        }
        ChunkGroupSnapshot.ChunkGroupEntry group = lookup.entry();
        int changed = updatePausedState(level, group.chunks(), true);
        return loweredTickets(groupIndex, group.label(), group.stats().chunkCount(), changed);
    }

    /**
     * 恢复指定分组的票据。
     */
    public static Component restore(ServerLevel level, int groupIndex) {
        ChunkGroupService.GroupLookup lookup = ChunkGroupService.resolve(level, groupIndex, ChunkGroupSnapshot.PauseMode.PAUSED_ONLY);
        if (lookup.failure() != null) {
            return toLookupError(lookup.failure(), false);
        }
        ChunkGroupSnapshot.ChunkGroupEntry group = lookup.entry();
        int changed = updatePausedState(level, group.chunks(), false);
        return restoredTickets(groupIndex, group.label(), group.stats().chunkCount(), changed);
    }

    /**
     * 恢复当前位置所在分组的票据。
     */
    public static Component restoreHere(ServerLevel level, BlockPos pos) {
        ChunkGroupService.GroupChunkLookup lookup = ChunkGroupService.resolveAtChunk(
                level,
                ChunkPos.asLong(pos),
                ChunkGroupSnapshot.PauseMode.PAUSED_ONLY
        );
        if (lookup.failure() != null) {
            return toLookupError(lookup.failure(), true);
        }
        ChunkGroupSnapshot.ChunkGroupEntry group = lookup.entry();
        int changed = updatePausedState(level, group.chunks(), false);
        return restoredTickets(
                lookup.groupIndex(),
                group.label(),
                group.stats().chunkCount(),
                changed
        );
    }

    public static void replayPendingManualPause(ServerLevel level) {
        ManualPauseData manualData = ManualPauseData.get(level);
        if (!manualData.needsReplay()) {
            return;
        }
        if (manualData.isEmpty()) {
            manualData.markReplayApplied();
            return;
        }

        TicketPauseService.applyPauseReasonToChunks(
                level,
                manualData.chunks(),
                true,
                IPauseableTicket.PAUSE_REASON_MANUAL
        );
        manualData.markReplayApplied();
    }

    /**
     * 列出 lowered 分组。
     */
    public static Component listLoweredGroups(ServerLevel level,
                                              String header,
                                              TicketPauseGroupState.Reason reasonFilter) {
        List<ChunkGroupSnapshot.ChunkGroupEntry> allGroups =
                ChunkGroupService.listGroups(level, ChunkGroupSnapshot.PauseMode.PAUSED_ONLY);
        Map<TicketOwner<?>, TicketPauseGroupState> pauseStates =
                collectPauseStates(level, allGroups);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = allGroups.stream()
                .filter(group -> reasonFilter == null
                        || pauseStates.getOrDefault(group.owner(), RUNNING).hasReason(reasonFilter))
                .sorted(Comparator
                        .comparingInt((ChunkGroupSnapshot.ChunkGroupEntry group) -> group.stats().blockEntityCount())
                        .reversed()
                        .thenComparing(group -> group.owner().toString()))
                .toList();
        return buildMessage(
                level,
                header,
                groups,
                pauseStates,
                ChunkGroupService.instance().indexMap(level, allGroups)
        );
    }

    public static Map<TicketOwner<?>, TicketPauseGroupState> collectPauseStates(
            ServerLevel level,
            List<ChunkGroupSnapshot.ChunkGroupEntry> groups
    ) {
        Map<TicketOwner<?>, TicketPauseGroupState> result = new HashMap<>();
        for (ChunkGroupSnapshot.ChunkGroupEntry group : groups) {
            result.put(group.owner(), resolvePauseState(level, group));
        }
        return result;
    }

    private static TicketPauseGroupState resolvePauseState(ServerLevel level,
                                                           ChunkGroupSnapshot.ChunkGroupEntry group) {
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        boolean manualPaused = false;
        boolean autoPaused = false;
        boolean idlePaused = false;
        for (long chunkPos : group.chunks()) {
            SortedArraySet<Ticket<?>> tickets = TicketUtils.getTickets(distanceManager).get(chunkPos);
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            for (Ticket<?> ticket : tickets) {
                if (TicketUtils.getOriginLevel(ticket) > TicketUtils.MAX_PAUSEABLE_LEVEL) {
                    continue;
                }
                int pauseMask = ((IPauseableTicket) (Object) ticket).serverWarashi$getPauseMask();
                if ((pauseMask & IPauseableTicket.PAUSE_REASON_MANUAL) != 0) {
                    manualPaused = true;
                }
                if ((pauseMask & IPauseableTicket.PAUSE_REASON_AUTO) != 0) {
                    autoPaused = true;
                }
                if ((pauseMask & IPauseableTicket.PAUSE_REASON_IDLE) != 0) {
                    idlePaused = true;
                }
                if (manualPaused && autoPaused && idlePaused) {
                    return new TicketPauseGroupState(true, true, true);
                }
            }
        }
        return new TicketPauseGroupState(manualPaused, autoPaused, idlePaused);
    }

    /**
     * 对目标区块集合的非系统票据设置暂停状态，并刷新区块 ticket level。
     */
    private static int updatePausedState(ServerLevel level,
                                         Set<Long> chunks,
                                         boolean paused) {
        ManualPauseData manualData = ManualPauseData.get(level);
        for (long chunkPos : chunks) {
            if (paused) {
                manualData.add(chunkPos);
            } else {
                manualData.remove(chunkPos);
            }
        }
        return TicketPauseService.applyPauseReasonToChunks(
                level,
                chunks,
                paused,
                IPauseableTicket.PAUSE_REASON_MANUAL
        );
    }

    private static Component toLookupError(ChunkGroupService.LookupFailure failure, boolean chunkLookup) {
        return switch (failure.reason()) {
            case NO_GROUPS -> chunkLookup
                    ? noLoweredGroupHere()
                    : noTicketGroupsFound();
            case INDEX_OUT_OF_RANGE, INDEX_MAPPING_MISSING ->
                    groupIndexOutOfRange(failure.maxStableIndex());
            case CHUNK_NOT_FOUND -> noLoweredGroupHere();
        };
    }

    private static Component noTicketGroupsFound() {
        return Component.literal("No ticket groups found.");
    }

    private static Component groupIndexOutOfRange(int max) {
        return Component.literal("Group index out of range. Max = " + max);
    }

    private static Component noLoweredGroupHere() {
        return Component.literal("No lowered ticket group found at current chunk.");
    }

    private static Component loweredTickets(int groupIndex, String label, int chunkCount, int updated) {
        return Component.literal("Lowered tickets for G" + groupIndex + " " + label
                        + " (chunks=" + chunkCount + ", updated=" + updated + ").")
                .withStyle(ChatFormatting.GREEN);
    }

    private static Component restoredTickets(int groupIndex, String label, int chunkCount, int updated) {
        return Component.literal("Restored tickets for G" + groupIndex + " " + label
                        + " (chunks=" + chunkCount + ", updated=" + updated + ").")
                .withStyle(ChatFormatting.GREEN);
    }

    private static Component buildMessage(ServerLevel level,
                                          String header,
                                          List<ChunkGroupSnapshot.ChunkGroupEntry> groups,
                                          Map<TicketOwner<?>, TicketPauseGroupState> pauseStates,
                                          Map<TicketOwner<?>, Integer> indexMap) {
        MutableComponent root = Component.literal(header + "\n").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Dimension: " + level.dimension().location() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Note: [R] only appears on manually paused groups.\n")
                        .withStyle(ChatFormatting.DARK_GRAY));
        if (groups.isEmpty()) {
            return root.append(Component.literal("No tickets found\n").withStyle(ChatFormatting.GRAY));
        }
        for (ChunkGroupSnapshot.ChunkGroupEntry group : groups) {
            root = root.append(buildGroupLine(
                    indexMap.getOrDefault(group.owner(), -1),
                    group,
                    pauseStates.getOrDefault(group.owner(), RUNNING)
            ));
        }
        return root;
    }

    private static MutableComponent buildGroupLine(int groupIndex,
                                                   ChunkGroupSnapshot.ChunkGroupEntry group,
                                                   TicketPauseGroupState pauseState) {
        ChunkGroupSnapshot.OwnerStats stats = group.stats();
        MutableComponent line = Component.empty()
                .append(group.owner().asComponent())
                .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("G" + groupIndex + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("C=" + stats.chunkCount() + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("BE=" + stats.blockEntityCount() + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("E=" + stats.entityCount() + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("P=" + pauseState.label()).withStyle(ChatFormatting.DARK_GRAY));
        if (pauseState.canRestore()) {
            line = line.append(Component.literal(" "))
                    .append(Component.literal("[R]")
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.GREEN)
                                    .withHoverEvent(new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Restore manual ticket lowering for this group")
                                    ))
                                    .withClickEvent(new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            "/warashi pause group restore " + groupIndex
                                    ))));
        }
        return line.append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
    }
}
