package com.moepus.serverwarashi.chunkperf;

import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfGroupView;
import com.moepus.serverwarashi.chunkperf.data.ChunkPerfSnapshot;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建性能分析相关的提示与报告消息。
 */
public final class ChunkPerfMessages {
    //spike 报告过滤阈值(即异常实体过滤阈值,0.05 ms)
    private static final long SPIKE_MIN_NANOS = 50_000L;

    private ChunkPerfMessages() {
    }

    /**
     * 没有可用分组时的提示。
     */
    public static Component noTicketGroupsFound() {
        return Component.literal("No ticket groups found.");
    }

    /**
     * 没有活动会话时的提示。
     */
    public static Component noActiveSession() {
        return Component.literal("No active chunk perf session.");
    }

    /**
     * 没有活动的分析会话时的提示。
     */
    public static Component noActiveAnalysis() {
        return Component.literal("No active perf analysis.");
    }

    /**
     * 已有分析在运行时的提示。
     */
    public static Component analysisAlreadyRunning() {
        return Component.literal("Another perf analysis is already running.");
    }

    /**
     * 没有活动的全分组分析会话时的提示。
     */
    public static Component noActiveGroupAnalysis() {
        return Component.literal("No active group analysis session.");
    }


    /**
     * 分组索引越界时的提示。
     *
     * @param max 最大有效索引
     */
    public static Component groupIndexOutOfRange(int max) {
        return Component.literal("Group index out of range. Max = " + max);
    }

    /**
     * 会话开始时的提示。
     */
    public static Component sessionStarted(ResourceKey<Level> dimension,
                                           Component ownerComponent,
                                           int groupIndex,
                                           int chunkCount,
                                           int blockEntityCount,
                                           int entityCount,
                                           int durationSec) {
        MutableComponent message = Component.literal("Chunk perf analysis started (" + durationSec + "s) >\n")
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Dimension=" + dimension.location() + "\n").withStyle(ChatFormatting.GRAY));
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ownerComponent)
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
        message = message.append(Component.literal("    (G" + groupIndex
                        + ", chunks=" + chunkCount
                        + ", BE=" + blockEntityCount
                        + ", E=" + entityCount
                        + ").\n").withStyle(ChatFormatting.GRAY));
        return message;
    }

    /**
     * 全分组分析开始提示。
     *
     * @param groupCount  分组数量
     * @param durationSec 持续秒数
     */
    public static Component allGroupsAnalysisStarted(ResourceKey<Level> dimension, int groupCount, int durationSec) {
        MutableComponent message = Component.literal("All group analysis started (" + durationSec + "s) >\n")
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Dimension=" + dimension.location()
                        + " | Groups=" + groupCount + "\n").withStyle(ChatFormatting.GRAY));
        return message;
    }

    /**
     * 降低票据时的提示。
     */
    public static Component loweredTickets(int groupIndex, String label, int chunkCount, int updated) {
        return Component.literal("Lowered tickets for G" + groupIndex + " " + label
                        + " (chunks=" + chunkCount + ", updated=" + updated + ").")
                .withStyle(ChatFormatting.GREEN);
    }

    /**
     * 恢复票据时的提示。
     */
    public static Component restoredTickets(int groupIndex, String label, int chunkCount, int updated) {
        return Component.literal("Restored tickets for G" + groupIndex + " " + label
                        + " (chunks=" + chunkCount + ", updated=" + updated + ").")
                .withStyle(ChatFormatting.GREEN);
    }

    /**
     * 无票据时的补充行。
     */
    public static Component noTicketsFoundLine() {
        return Component.literal("No tickets found\n");
    }

    /**
     * 格式化拥有者统计到聊天消息。
     */
    public static MutableComponent formatOwnerStatsToComponent(
            String header,
            ResourceKey<Level> dimension,
            List<ChunkPerfSnapshot.ChunkPerfGroupEntry> groups,
            ChunkPerfSnapshot.SortMode sortMode,
            ChunkPerfSnapshot.PauseMode pauseMode,
            boolean showActions,
            Map<TicketOwner<?>, Integer> stableIndex,
            Map<TicketOwner<?>, ChunkPerfGroupView.GroupPauseState> pauseStates
    ) {
        MutableComponent root = Component.literal(header + "\n").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Dimension: " + dimension.location() + "\n").withStyle(ChatFormatting.GRAY));
        if (pauseMode == ChunkPerfSnapshot.PauseMode.PAUSED_ONLY) {
            root = root.append(Component.literal("Note: [R] only appears on manually paused groups.\n")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (groups.isEmpty()) {
            return root.append(noTicketsFoundLine());
        }
        List<ChunkPerfSnapshot.ChunkPerfGroupEntry> baseEntries = groups.stream()
                .sorted(Comparator
                        .comparingInt((ChunkPerfSnapshot.ChunkPerfGroupEntry entry) -> entry.stats().blockEntityCount())
                        .reversed()
                        .thenComparing(entry -> entry.owner().toString()))
                .toList();
        HashMap<TicketOwner<?>, Integer> baseIndex = new HashMap<>();
        for (int i = 0; i < baseEntries.size(); i++) {
            baseIndex.put(baseEntries.get(i).owner(), i);
        }

        Comparator<ChunkPerfSnapshot.ChunkPerfGroupEntry> comparator = switch (sortMode) {
            case ENTITY -> Comparator
                    .comparingInt((ChunkPerfSnapshot.ChunkPerfGroupEntry entry) -> entry.stats().entityCount())
                    .reversed()
                    .thenComparing(entry -> entry.owner().toString());
            case BLOCK_ENTITY -> Comparator
                    .comparingInt((ChunkPerfSnapshot.ChunkPerfGroupEntry entry) -> entry.stats().blockEntityCount())
                    .reversed()
                    .thenComparing(entry -> entry.owner().toString());
        };
        List<ChunkPerfSnapshot.ChunkPerfGroupEntry> entries = groups.stream().sorted(comparator).toList();
        boolean showRestore = pauseMode == ChunkPerfSnapshot.PauseMode.PAUSED_ONLY;
        for (int i = 0; i < entries.size(); i++) {
            ChunkPerfSnapshot.ChunkPerfGroupEntry entry = entries.get(i);
            TicketOwner<?> owner = entry.owner();
            ChunkPerfSnapshot.OwnerStats stats = entry.stats();
            ChunkPerfGroupView.GroupPauseState pauseState = pauseStates.getOrDefault(
                    owner,
                    new ChunkPerfGroupView.GroupPauseState(false, false)
            );
            int groupIndex;
            if (stableIndex != null) {
                groupIndex = stableIndex.getOrDefault(owner, baseIndex.getOrDefault(owner, i));
            } else {
                groupIndex = baseIndex.getOrDefault(owner, i);
            }
            MutableComponent line = Component.empty()
                    .append(owner.asComponent())
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("G" + groupIndex + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("C=" + stats.chunkCount() + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("BE=" + stats.blockEntityCount() + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("E=" + stats.entityCount() + " ").withStyle(ChatFormatting.GRAY));
            if (pauseState.isPaused()) {
                line = line.append(Component.literal(" "))
                        .append(Component.literal("P=" + pauseState.label()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (showActions) {
                if (showRestore) {
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
                                                        "/warashi perf group restore " + groupIndex
                                                ))));
                    }
                } else {
                    line = line.append(Component.literal(" "))
                            .append(Component.literal("[A]")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.LIGHT_PURPLE)
                                            .withHoverEvent(new HoverEvent(
                                                    HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal("Run a 60s analysis and auto-report")
                                            ))
                                            .withClickEvent(new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    "/warashi perf analyze start group " + groupIndex + " 60"
                                            ))))
                            .append(Component.literal(" "))
                            .append(Component.literal("[L]")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.RED)
                                            .withHoverEvent(new HoverEvent(
                                                    HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal("Lower ticket level for this group")
                                            ))
                                            .withClickEvent(new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    "/warashi perf group lower " + groupIndex
                                            ))));
                }
            }
            line = line.append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
            root = root.append(line);
        }
        return root;
    }

    /**
     * 构建全分组性能分析报告。
     */
    public static MutableComponent buildGroupMsptReport(ResourceKey<Level> dimension,
                                                        List<GroupMsptEntry> entries,
                                                        long serverTickCount,
                                                        Duration elapsed) {
        double totalReportedMspt = 0.0;
        MutableComponent root = Component.literal("Group MSPT Report\n")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("====================================\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Target: " + dimension.location()
                                + " | Groups=" + entries.size()
                                + " | Elapsed=" + elapsed.toSeconds() + "s"
                                + " | ServerTicks=" + serverTickCount + "\n")
                        .withStyle(ChatFormatting.GRAY));
        root = root.append(Component.literal("Note: groups with mspt < 0.01 are omitted.\n")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (entries.isEmpty()) {
            return root.append(noTicketsFoundLine());
        }
        for (GroupMsptEntry entry : entries) {
            double totalMs = entry.totalNanos() / 1_000_000.0;
            double mspt = serverTickCount == 0 ? 0.0 : totalMs / serverTickCount;
            if (mspt < 0.01) {
                continue;
            }
            totalReportedMspt += mspt;
            MutableComponent line = Component.empty()
                    .append(entry.owner().asComponent())
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("G" + entry.groupIndex() + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("C=%d, BE=%d, E=%d",
                                    entry.chunkCount(),
                                    entry.blockEntityCount(),
                                    entry.entityCount()))
                            .withStyle(ChatFormatting.GRAY));
            line = line.append(Component.literal(" "))
                    .append(Component.literal(String.format("mspt=%.2f", mspt))
                            .withStyle(ChatFormatting.GOLD));
            line = line.append(Component.literal(" "))
                    .append(Component.literal("[L]")
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.RED)
                                    .withHoverEvent(new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Lower ticket level for this group")
                                    ))
                                    .withClickEvent(new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            "/warashi perf group lower " + entry.groupIndex()
                                    ))));
            line = line.append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
            root = root.append(line);
        }
        root = root.append(Component.literal(String.format("Reported total mspt=%.2f\n", totalReportedMspt))
                .withStyle(ChatFormatting.GOLD));
        return root;
    }

    /**
     * 构建性能会话的最终报告。
     */
    public static Component buildReport(ResourceKey<Level> dimension,
                                        int groupIndex,
                                        String ownerLabel,
                                        int chunkCount,
                                        int blockEntityCount,
                                        int entityCount,
                                        long serverTickCount,
                                        long beTotalNanos,
                                        long beMaxNanos,
                                        long entityTotalNanos,
                                        long entityMaxNanos,
                                        long chunkTotalNanos,
                                        long chunkMaxNanos,
                                        Long2LongOpenHashMap chunkLoadTotals,
                                        Object2LongOpenHashMap<String> blockEntitySpikeNanos,
                                        Map<String, String> blockEntitySpikeLabels,
                                        Object2LongOpenHashMap<String> typeTotals,
                                        Object2LongOpenHashMap<String> entitySpikeNanos,
                                        Map<String, String> entitySpikeLabels,
                                        Object2LongOpenHashMap<String> entityTotals,
                                        Duration elapsed) {
        double beTotalMs = beTotalNanos / 1_000_000.0;
        double beMaxMs = beMaxNanos / 1_000_000.0;
        double entityTotalMs = entityTotalNanos / 1_000_000.0;
        double entityMaxMs = entityMaxNanos / 1_000_000.0;
        double chunkTotalMs = chunkTotalNanos / 1_000_000.0;
        double chunkMaxMs = chunkMaxNanos / 1_000_000.0;
        double combinedTotalMs = beTotalMs + entityTotalMs + chunkTotalMs;
        double totalPerTickMs = serverTickCount == 0 ? 0.0 : combinedTotalMs / serverTickCount;
        double beMspt = serverTickCount == 0 ? 0.0 : beTotalMs / serverTickCount;
        double entityMspt = serverTickCount == 0 ? 0.0 : entityTotalMs / serverTickCount;
        double chunkMspt = serverTickCount == 0 ? 0.0 : chunkTotalMs / serverTickCount;

        MutableComponent root = Component.literal("ServerWarashi Profiler Report\n")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("====================================\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Target: " + dimension.location() + " | G" + groupIndex + " " + ownerLabel + " ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[L]")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.RED)
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Lower ticket level for this group")
                                ))
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/warashi perf group lower " + groupIndex
                                ))))
                .append(Component.literal("\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Chunks: " + chunkCount
                                + ", BlockEntity=" + blockEntityCount
                                + ", Entity=" + entityCount
                                + "  | Elapsed: " + elapsed.toSeconds() + "s\n")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("---- summary ----\n").withStyle(ChatFormatting.DARK_AQUA));
        if (totalPerTickMs >= 0.01) {
            root = root.append(Component.literal(String.format("ServerTicks: %d, mspt=%.2fms/tick\n",
                    serverTickCount, totalPerTickMs)).withStyle(ChatFormatting.GOLD));
        }
        if (beMspt >= 0.01) {
            root = root.append(Component.literal(String.format("block entities (mspt=%.2fms/tick)>max=%.3fms\n",
                    beMspt, beMaxMs)).withStyle(ChatFormatting.GREEN));
        }
        if (entityMspt >= 0.01) {
            root = root.append(Component.literal(String.format("entities (mspt=%.2fms/tick)>max=%.3fms\n",
                    entityMspt, entityMaxMs)).withStyle(ChatFormatting.YELLOW));
        }
        if (chunkMspt >= 0.01) {
            root = root.append(Component.literal(String.format("chunks (mspt=%.2fms/tick)>max=%.3fms\n",
                    chunkMspt, chunkMaxMs)).withStyle(ChatFormatting.BLUE));
        }

        root = appendCollapsedDetailSection(
                root,
                "top5 chunks",
                buildTopChunkLines(chunkLoadTotals, serverTickCount),
                ChatFormatting.BLUE
        );

        root = appendCollapsedDetailSection(
                root,
                "spike block entities",
                buildSpikeLines(blockEntitySpikeNanos, blockEntitySpikeLabels),
                ChatFormatting.GREEN
        );

        root = appendCollapsedDetailSection(
                root,
                "top5 block entities",
                buildTopTypeLines(typeTotals, serverTickCount),
                ChatFormatting.GREEN
        );

        root = appendCollapsedDetailSection(
                root,
                "spike entities",
                buildSpikeLines(entitySpikeNanos, entitySpikeLabels),
                ChatFormatting.YELLOW
        );

        root = appendCollapsedDetailSection(
                root,
                "top5 entities",
                buildTopTypeLines(entityTotals, serverTickCount),
                ChatFormatting.YELLOW
        );

        return root;
    }

    private static MutableComponent appendCollapsedDetailSection(MutableComponent root,
                                                                 String title,
                                                                 List<String> lines,
                                                                 ChatFormatting color) {
        if (lines.isEmpty()) {
            return root;
        }
        String details = title + "\n" + String.join("\n", lines);
        MutableComponent copyable = Component.literal("[" + title + "]")
                .withStyle(Style.EMPTY
                        .withColor(color)
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal(details + "\n\nClick to copy")
                        ))
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                details
                        )));
        return root.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(copyable)
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static List<String> buildTopChunkLines(Long2LongOpenHashMap chunkLoadTotals, long serverTickCount) {
        if (chunkLoadTotals.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        chunkLoadTotals.long2LongEntrySet().stream()
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .limit(5)
                .forEach(entry -> {
                    double chunkMs = entry.getLongValue() / 1_000_000.0;
                    double chunkEntryMspt = serverTickCount == 0 ? 0.0 : chunkMs / serverTickCount;
                    ChunkPos chunkPos = new ChunkPos(entry.getLongKey());
                    lines.add(String.format("- chunk(%d,%d) (be+e mspt=%.2fms/tick)",
                            chunkPos.x, chunkPos.z, chunkEntryMspt));
                });
        return lines;
    }

    private static List<String> buildSpikeLines(Object2LongOpenHashMap<String> spikeNanos,
                                                Map<String, String> spikeLabels) {
        if (spikeNanos.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        spikeNanos.object2LongEntrySet().stream()
                .filter(entry -> entry.getLongValue() >= SPIKE_MIN_NANOS)
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .limit(5)
                .forEach(entry -> {
                    double maxMs = entry.getLongValue() / 1_000_000.0;
                    String label = spikeLabels.getOrDefault(entry.getKey(), entry.getKey());
                    lines.add(String.format("- %s (max=%.3fms)", label, maxMs));
                });
        return lines;
    }

    private static List<String> buildTopTypeLines(Object2LongOpenHashMap<String> totals,
                                                  long serverTickCount) {
        if (totals.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        totals.object2LongEntrySet().stream()
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .limit(5)
                .forEach(entry -> {
                    double typeMs = entry.getLongValue() / 1_000_000.0;
                    double typeMspt = serverTickCount == 0 ? 0.0 : typeMs / serverTickCount;
                    if (typeMspt < 0.01) {
                        return;
                    }
                    lines.add(String.format("- %s (mspt=%.2fms/tick)", entry.getKey(), typeMspt));
                });
        return lines;
    }

    /**
     * 单个分组的性能汇总行。
     */
    public record GroupMsptEntry(int groupIndex,
                                 TicketOwner<?> owner,
                                 int chunkCount,
                                 int blockEntityCount,
                                 int entityCount,
                                 long beTotalNanos,
                                 long entityTotalNanos,
                                 long chunkTotalNanos) {
        /**
         * 计算三类耗时的总纳秒。
         */
        public long totalNanos() {
            return beTotalNanos + entityTotalNanos + chunkTotalNanos;
        }
    }

}
