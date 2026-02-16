package com.moepus.serverwarashi.chunkperf.entry;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.moepus.serverwarashi.chunkperf.core.ChunkPerfManager;
import com.moepus.serverwarashi.chunkperf.ticket.ChunkPerfTickets;
import net.minecraft.commands.CommandSourceStack;

/**
 * 注册 {@code /warashi perf} 子命令。
 */
public final class ChunkPerfCommands {
    private ChunkPerfCommands() {
    }

    /**
     * 将 {@code /warashi perf ...} 挂载到根命令节点。
     *
     * @param root 根命令节点
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(net.minecraft.commands.Commands.literal("perf")
                .then(net.minecraft.commands.Commands.literal("list")
                        .executes(context -> listGroups(context, ChunkPerfTickets.PauseMode.ACTIVE_ONLY, "Ticket groups:", ChunkPerfTickets.SortMode.BLOCK_ENTITY))
                        .then(net.minecraft.commands.Commands.literal("entity")
                                .executes(context -> listGroups(context, ChunkPerfTickets.PauseMode.ACTIVE_ONLY, "Ticket groups:", ChunkPerfTickets.SortMode.ENTITY)))
                        .then(net.minecraft.commands.Commands.literal("blockentity")
                                .executes(context -> listGroups(context, ChunkPerfTickets.PauseMode.ACTIVE_ONLY, "Ticket groups:", ChunkPerfTickets.SortMode.BLOCK_ENTITY)))
                        .then(net.minecraft.commands.Commands.literal("lowered")
                                .executes(context -> listGroups(context, ChunkPerfTickets.PauseMode.PAUSED_ONLY, "Lowered ticket groups:", ChunkPerfTickets.SortMode.BLOCK_ENTITY))
                                .then(net.minecraft.commands.Commands.literal("entity")
                                        .executes(context -> listGroups(context, ChunkPerfTickets.PauseMode.PAUSED_ONLY, "Lowered ticket groups:", ChunkPerfTickets.SortMode.ENTITY)))
                                .then(net.minecraft.commands.Commands.literal("blockentity")
                                        .executes(context -> listGroups(context, ChunkPerfTickets.PauseMode.PAUSED_ONLY, "Lowered ticket groups:", ChunkPerfTickets.SortMode.BLOCK_ENTITY)))
                        )
                )
                .then(net.minecraft.commands.Commands.literal("start")
                        .then(net.minecraft.commands.Commands.literal("group")
                                .then(net.minecraft.commands.Commands.argument("group", IntegerArgumentType.integer(0))
                                        .executes(ChunkPerfCommands::startPerfGroup)
                                        .then(net.minecraft.commands.Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                .executes(ChunkPerfCommands::startPerfGroup)
                                        )
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("all")
                                .executes(ChunkPerfCommands::startAllGroups)
                                .then(net.minecraft.commands.Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ChunkPerfCommands::startAllGroups)
                                )
                        )
                )
                .then(net.minecraft.commands.Commands.literal("lower")
                        .then(net.minecraft.commands.Commands.argument("group", IntegerArgumentType.integer(0))
                                .executes(ChunkPerfCommands::lowerPerfGroup)
                        )
                )
                .then(net.minecraft.commands.Commands.literal("restore")
                        .then(net.minecraft.commands.Commands.argument("group", IntegerArgumentType.integer(0))
                                .executes(ChunkPerfCommands::restorePerfGroup)
                        )
                )
                .then(net.minecraft.commands.Commands.literal("stop")
                        .executes(ChunkPerfCommands::stopPerfGroup)
                )
        );
    }

    /**
     * 启动性能会话或定时分析。
     *
     * @param context 命令上下文
     * @return 命令结果
     */
    private static int startPerfGroup(CommandContext<CommandSourceStack> context) {
        int groupIndex = IntegerArgumentType.getInteger(context, "group");
        int seconds = getOptionalSeconds(context);
        context.getSource().sendSuccess(() -> {
            if (seconds > 0) {
                return ChunkPerfManager.start(context.getSource(), groupIndex, seconds);
            }
            return ChunkPerfManager.start(context.getSource().getLevel(), groupIndex);
        }, false);
        return 1;
    }

    /**
     * 降低指定分组的票据等级。
     *
     * @param context 命令上下文
     * @return 命令结果
     */
    private static int lowerPerfGroup(CommandContext<CommandSourceStack> context) {
        int groupIndex = IntegerArgumentType.getInteger(context, "group");
        context.getSource().sendSuccess(() ->
                ChunkPerfManager.lower(context.getSource().getLevel(), groupIndex), false);
        return 1;
    }

    /**
     * 恢复指定分组的票据等级。
     *
     * @param context 命令上下文
     * @return 命令结果
     */
    private static int restorePerfGroup(CommandContext<CommandSourceStack> context) {
        int groupIndex = IntegerArgumentType.getInteger(context, "group");
        context.getSource().sendSuccess(() ->
                ChunkPerfManager.restore(context.getSource().getLevel(), groupIndex), false);
        return 1;
    }

    /**
     * 停止当前会话并输出报告。
     *
     * @param context 命令上下文
     * @return 命令结果
     */
    private static int stopPerfGroup(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                ChunkPerfManager.stop(context.getSource().getLevel()), false);
        return 1;
    }

    /**
     * 启动全分组性能分析会话或定时分析。
     *
     * @param context 命令上下文
     * @return 命令结果
     */
    private static int startAllGroups(CommandContext<CommandSourceStack> context) {
        int seconds = getOptionalSeconds(context);
        context.getSource().sendSuccess(() -> {
            if (seconds > 0) {
                return ChunkPerfManager.startAll(context.getSource(), seconds);
            }
            return ChunkPerfManager.startAll(context.getSource().getLevel());
        }, false);
        return 1;
    }


    private static int listGroups(CommandContext<CommandSourceStack> context,
                                  ChunkPerfTickets.PauseMode pauseMode,
                                  String header,
                                  ChunkPerfTickets.SortMode sortMode) {
        context.getSource().sendSuccess(() ->
                ChunkPerfManager.listGroups(
                        context.getSource().getLevel(),
                        pauseMode,
                        header,
                        sortMode
                ), false);
        return 1;
    }

    /**
     * 读取可选的秒参数；不存在时返回 0。
     */
    private static int getOptionalSeconds(CommandContext<CommandSourceStack> context) {
        if (context.getNodes().stream().noneMatch(node -> "seconds".equals(node.getNode().getName()))) {
            return 0;
        }
        return IntegerArgumentType.getInteger(context, "seconds");
    }
}
