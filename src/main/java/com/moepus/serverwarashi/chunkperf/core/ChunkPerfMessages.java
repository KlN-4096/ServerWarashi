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
            ChunkPerfTickets.SortMode sortMode
    ) {
        MutableComponent message = formatOwnerStatsToComponent(header, snapshot.ownerStats(), sortMode);
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
            ChunkPerfTickets.SortMode sortMode
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
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<TicketOwner<?>, ChunkPerfTickets.OwnerStats> entry = entries.get(i);
            TicketOwner<?> owner = entry.getKey();
            ChunkPerfTickets.OwnerStats stats = entry.getValue();
            int groupIndex = baseIndex.getOrDefault(owner, i);
            MutableComponent line = Component.empty()
                    .append(owner.asComponent())
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("G" + groupIndex + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("C=" + stats.chunkCount() + " ").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal("BE=" + stats.blockEntityCount() + " ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("E=" + stats.entityCount() + " ").withStyle(ChatFormatting.YELLOW));
            line = line.append(Component.literal(" "))
                    .append(Component.literal("[analyze]")
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.LIGHT_PURPLE)
                                    .withHoverEvent(new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Run a 60s analysis and auto-report")
                                    ))
                                    .withClickEvent(new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            "/warashi perf start " + groupIndex + " 60"
                                    ))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[lower]")
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
                    .append(Component.literal(" "))
                    .append(Component.literal("[restore]")
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
                .append(Component.literal("Target: " + dimension.location() + " | G" + groupIndex + " " + ownerLabel + "\n")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Chunks: " + chunkCount
                                + ", BlockEntity=" + blockEntityCount
                                + ", Entity=" + entityCount
                                + "  | Elapsed: " + elapsed.toSeconds() + "s\n")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("Total: %.3fms, ServerTicks: %d, mspt=%.6fms/tick\n",
                                combinedTotalMs, serverTickCount, totalPerTickMs))
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("---- summary ----\n").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(String.format("block entities (mspt=%.6fms/tick)>total=%.3fms, max=%.3fms, ticks=%d\n",
                        beMspt, beTotalMs, beMaxMs, beTickCount)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(String.format("entities (mspt=%.6fms/tick)>total=%.3fms, max=%.3fms, ticks=%d\n",
                        entityMspt, entityTotalMs, entityMaxMs, entityTickCount)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("chunks (mspt=%.6fms/tick)>total=%.3fms, max=%.3fms, ticks=%d\n",
                        chunkMspt, chunkTotalMs, chunkMaxMs, chunkTickCount)).withStyle(ChatFormatting.BLUE));

        root = root.append(Component.literal("---- top block entities ----\n").withStyle(ChatFormatting.DARK_GREEN));
        var topBe = typeTotals.object2LongEntrySet().stream()
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .limit(5)
                .toList();
        for (var entry : topBe) {
            long count = typeCounts.getLong(entry.getKey());
            double typeMs = entry.getLongValue() / 1_000_000.0;
            double typeMspt = serverTickCount == 0 ? 0.0 : typeMs / serverTickCount;
            root = root.append(Component.literal(String.format(" - %s (mspt=%.6fms/tick): total=%.3fms, ticks=%d\n",
                    entry.getKey(), typeMspt, typeMs, count)).withStyle(ChatFormatting.GREEN));
        }

        root = root.append(Component.literal("---- top entities ----\n").withStyle(ChatFormatting.GOLD));
        var topEntities = entityTotals.object2LongEntrySet().stream()
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .limit(5)
                .toList();
        for (var entry : topEntities) {
            long count = entityCounts.getLong(entry.getKey());
            double typeMs = entry.getLongValue() / 1_000_000.0;
            double typeMspt = serverTickCount == 0 ? 0.0 : typeMs / serverTickCount;
            root = root.append(Component.literal(String.format(" - %s (mspt=%.6fms/tick): total=%.3fms, ticks=%d\n",
                    entry.getKey(), typeMspt, typeMs, count)).withStyle(ChatFormatting.YELLOW));
        }

        return root;
    }

}
