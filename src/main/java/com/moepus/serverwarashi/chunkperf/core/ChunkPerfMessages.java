package com.moepus.serverwarashi.chunkperf.core;

import com.moepus.serverwarashi.TicketOwner;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建性能分析相关的提示与报告消息。
 */
public final class ChunkPerfMessages {
    private ChunkPerfMessages() {
    }

    /**
     * 没有可用分组时的提示。
     */
    public static Component noTicketGroupsFound() {
        return Component.literal("No ticket groups found.");
    }

    /**
     * 没有已降低分组时的提示。
     */
    public static Component noLoweredTicketGroupsFound() {return Component.literal("No lowered ticket groups found.");}

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
     * 会话开始时的提示（{@code durationSec > 0} 表示定时分析）。
     */
    public static Component sessionStarted(Component ownerComponent,
                                    int groupIndex,
                                    int chunkCount,
                                    int blockEntityCount,
                                    int entityCount,
                                    int durationSec) {
        String header = durationSec > 0
                ? "Chunk perf analysis started (" + durationSec + "s) >\n"
                : "Chunk perf session started >\n";
        MutableComponent message = Component.literal(header)
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ownerComponent)
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
        message = message.append(Component.literal("    (G" + groupIndex
                        + ", chunks=" + chunkCount
                        + ", BE=" + blockEntityCount
                        + ", E=" + entityCount
                        + ").\n").withStyle(ChatFormatting.GRAY));
        if (durationSec == 0) {
            message = message.append(Component.literal("    Use /warashi perf stop to end.\n")
                    .withStyle(ChatFormatting.GRAY));
        }
        return message;
    }

    /**
     * 全分组分析开始提示。
     *
     * @param groupCount  分组数量
     * @param durationSec 持续秒数，{@code 0} 表示不启用定时分析
     */
    public static Component allGroupsAnalysisStarted(int groupCount, int durationSec) {
        String header = durationSec > 0
                ? "All group analysis started (" + durationSec + "s) >\n"
                : "All group analysis started >\n";
        MutableComponent message = Component.literal(header)
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Groups=" + groupCount + "\n").withStyle(ChatFormatting.GRAY));
        if (durationSec == 0) {
            message = message.append(Component.literal("    Use /warashi perf stop to end.\n")
                    .withStyle(ChatFormatting.GRAY));
        }
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
     * 根据已有快照构建拥有者统计输出。
     *
     * @param header 输出标题文本
     * @param snapshot 统计快照
     * @param sortMode 排序模式
     * @return 格式化后的消息组件
     */
    public static MutableComponent buildOwnerStatsComponent(
            String header,
            ChunkPerfTickets.OwnerStatsSnapshot snapshot,
            ChunkPerfTickets.SortMode sortMode,
            ChunkPerfTickets.PauseMode pauseMode,
            boolean showActions,
            Map<TicketOwner<?>, Integer> stableIndex
    ) {
        MutableComponent message = formatOwnerStatsToComponent(header, snapshot.ownerStats(), sortMode, pauseMode, showActions, stableIndex);
        if (snapshot.ownerMap().isEmpty()) {
            message.append(noTicketsFoundLine());
        }
        return message;
    }

    /**
     * 格式化拥有者统计到聊天消息。
     */
    public static MutableComponent formatOwnerStatsToComponent(
            String header,
            HashMap<TicketOwner<?>, ChunkPerfTickets.OwnerStats> ownerStats,
            ChunkPerfTickets.SortMode sortMode,
            ChunkPerfTickets.PauseMode pauseMode,
            boolean showActions,
            Map<TicketOwner<?>, Integer> stableIndex
    ) {
        MutableComponent root = Component.literal(header + "\n").withStyle(ChatFormatting.AQUA);
        List<Map.Entry<TicketOwner<?>, ChunkPerfTickets.OwnerStats>> baseEntries =
                ChunkPerfTickets.sortOwnerStats(ownerStats, ChunkPerfTickets.SortMode.BLOCK_ENTITY);
        HashMap<TicketOwner<?>, Integer> baseIndex = new HashMap<>();
        for (int i = 0; i < baseEntries.size(); i++) {
            baseIndex.put(baseEntries.get(i).getKey(), i);
        }

        List<Map.Entry<TicketOwner<?>, ChunkPerfTickets.OwnerStats>> entries =
                ChunkPerfTickets.sortOwnerStats(ownerStats, sortMode);
        boolean showRestore = pauseMode == ChunkPerfTickets.PauseMode.PAUSED_ONLY;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<TicketOwner<?>, ChunkPerfTickets.OwnerStats> entry = entries.get(i);
            TicketOwner<?> owner = entry.getKey();
            ChunkPerfTickets.OwnerStats stats = entry.getValue();
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
                    .append(Component.literal("C=" + stats.chunkCount() + " ").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal("BE=" + stats.blockEntityCount() + " ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("E=" + stats.entityCount() + " ").withStyle(ChatFormatting.YELLOW));
            if (showActions) {
                if (showRestore) {
                    line = line.append(Component.literal(" "))
                            .append(Component.literal("[R]")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.GREEN)
                                            .withHoverEvent(new HoverEvent(
                                                    HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal("Restore ticket level for this group")
                                            ))
                                            .withClickEvent(new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    "/warashi perf restore " + groupIndex
                                            ))));
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
                                                    "/warashi perf start group " + groupIndex + " 60"
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
                                                    "/warashi perf lower " + groupIndex
                                            ))));
                }
            }
            line = line.append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
            root = root.append(line);
        }
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
                                 long beTickCount,
                                 long entityTotalNanos,
                                 long entityMaxNanos,
                                 long entityTickCount,
                                 long chunkTotalNanos,
                                 long chunkMaxNanos,
                                 long chunkTickCount,
                                 Object2LongOpenHashMap<String> typeTotals,
                                 Object2LongOpenHashMap<String> typeCounts,
                                 Object2LongOpenHashMap<String> entityTotals,
                                 Object2LongOpenHashMap<String> entityCounts,
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
                                        "/warashi perf lower " + groupIndex
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
            root = root.append(Component.literal(String.format("block entities (mspt=%.2fms/tick)>max=%.3fms, ticks=%d\n",
                    beMspt, beMaxMs, beTickCount)).withStyle(ChatFormatting.GREEN));
        }
        if (entityMspt >= 0.01) {
            root = root.append(Component.literal(String.format("entities (mspt=%.2fms/tick)>max=%.3fms, ticks=%d\n",
                    entityMspt, entityMaxMs, entityTickCount)).withStyle(ChatFormatting.YELLOW));
        }
        if (chunkMspt >= 0.01) {
            root = root.append(Component.literal(String.format("chunks (mspt=%.2fms/tick)>max=%.3fms, ticks=%d\n",
                    chunkMspt, chunkMaxMs, chunkTickCount)).withStyle(ChatFormatting.BLUE));
        }

        if (!typeTotals.isEmpty()) {
            var topBe = typeTotals.object2LongEntrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                    .limit(5)
                    .toList();
            boolean headerAdded = false;
            for (var entry : topBe) {
                long count = typeCounts.getLong(entry.getKey());
                double typeMs = entry.getLongValue() / 1_000_000.0;
                double typeMspt = serverTickCount == 0 ? 0.0 : typeMs / serverTickCount;
                if (typeMspt < 0.01) {
                    continue;
                }
                if (!headerAdded) {
                    root = root.append(Component.literal("---- top block entities ----\n").withStyle(ChatFormatting.DARK_GREEN));
                    headerAdded = true;
                }
                root = root.append(Component.literal(String.format(" - %s (mspt=%.2fms/tick): ticks=%d\n",
                        entry.getKey(), typeMspt, count)).withStyle(ChatFormatting.GREEN));
            }
        }

        if (!entityTotals.isEmpty()) {
            var topEntities = entityTotals.object2LongEntrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                    .limit(5)
                    .toList();
            boolean headerAdded = false;
            for (var entry : topEntities) {
                long count = entityCounts.getLong(entry.getKey());
                double typeMs = entry.getLongValue() / 1_000_000.0;
                double typeMspt = serverTickCount == 0 ? 0.0 : typeMs / serverTickCount;
                if (typeMspt < 0.01) {
                    continue;
                }
                if (!headerAdded) {
                    root = root.append(Component.literal("---- top entities ----\n").withStyle(ChatFormatting.GOLD));
                    headerAdded = true;
                }
                root = root.append(Component.literal(String.format(" - %s (mspt=%.2fms/tick): ticks=%d\n",
                        entry.getKey(), typeMspt, count)).withStyle(ChatFormatting.YELLOW));
            }
        }

        return root;
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
        public long totalNanos() {
            return beTotalNanos + entityTotalNanos + chunkTotalNanos;
        }
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
                                            "/warashi perf lower " + entry.groupIndex()
                                    ))));
            line = line.append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
            root = root.append(line);
        }
        root = root.append(Component.literal(String.format("Reported total mspt=%.2f\n", totalReportedMspt))
                .withStyle(ChatFormatting.GOLD));
        return root;
    }

}
