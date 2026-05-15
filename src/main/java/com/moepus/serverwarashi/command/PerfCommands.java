package com.moepus.serverwarashi.command;

import com.moepus.serverwarashi.modules.performance.TicketPerfApi;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.moepus.serverwarashi.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.group.ChunkGroupSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * ChunkPerf 命令入口。
 * 层级：命令入口层。
 * 上游：WarashiCommands。下游：TicketPerfApi。
 */
public final class PerfCommands {
    private PerfCommands() {
    }

    /**
     * 将纯性能相关的 {@code /warashi perf ...} 子命令挂载到根节点。
     *
     * @param root 根命令节点
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(net.minecraft.commands.Commands.literal("perf")
                .then(net.minecraft.commands.Commands.literal("list")
                        .then(net.minecraft.commands.Commands.literal("running")
                                .executes(context -> listGroups(context, ChunkGroupSnapshot.PauseMode.ACTIVE_ONLY, "Running ticket groups:", ChunkGroupSnapshot.SortMode.BLOCK_ENTITY))
                                .then(net.minecraft.commands.Commands.literal("entity")
                                        .executes(context -> listGroups(context, ChunkGroupSnapshot.PauseMode.ACTIVE_ONLY, "Running ticket groups:", ChunkGroupSnapshot.SortMode.ENTITY)))
                                .then(net.minecraft.commands.Commands.literal("blockentity")
                                        .executes(context -> listGroups(context, ChunkGroupSnapshot.PauseMode.ACTIVE_ONLY, "Running ticket groups:", ChunkGroupSnapshot.SortMode.BLOCK_ENTITY)))
                        )
                )
                .then(net.minecraft.commands.Commands.literal("dump")
                        .executes(PerfCommands::dumpTickets)
                )
                .then(net.minecraft.commands.Commands.literal("analyze")
                        .then(net.minecraft.commands.Commands.literal("start")
                                .then(net.minecraft.commands.Commands.literal("all")
                                        .executes(PerfCommands::startAllGroups)
                                        .then(net.minecraft.commands.Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                .executes(PerfCommands::startAllGroups)
                                        )
                                )
                                .then(net.minecraft.commands.Commands.literal("here")
                                        .executes(ctx -> startPerfHere(ctx, null))
                                        .then(net.minecraft.commands.Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                .executes(ctx -> startPerfHere(ctx, "seconds"))
                                        )
                                )
                                .then(net.minecraft.commands.Commands.literal("at")
                                        .then(net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> startPerfAt(ctx, null))
                                                .then(net.minecraft.commands.Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                        .executes(ctx -> startPerfAt(ctx, "seconds"))
                                                )
                                        )
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("stop")
                                .executes(PerfCommands::stopPerfGroup)
                        )
                )
        );
    }

    private static int startPerfHere(CommandContext<CommandSourceStack> context, String secondsArg) {
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        return runStartAt(context, pos, secondsArg);
    }

    private static int startPerfAt(CommandContext<CommandSourceStack> context, String secondsArg) {
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        return runStartAt(context, pos, secondsArg);
    }

    private static int runStartAt(CommandContext<CommandSourceStack> context,
                                  BlockPos pos,
                                  String secondsArg) {
        int seconds = secondsArg == null
                ? defaultAnalyzeSeconds()
                : IntegerArgumentType.getInteger(context, secondsArg);
        UUID playerId = getPlayerId(context.getSource());
        context.getSource().sendSuccess(() ->
                TicketPerfApi.start(
                        context.getSource().getLevel(),
                        pos,
                        seconds,
                        playerId
                ), false);
        return 1;
    }

    private static int stopPerfGroup(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                TicketPerfApi.stop(context.getSource().getLevel()), false);
        return 1;
    }

    private static int startAllGroups(CommandContext<CommandSourceStack> context) {
        int seconds = getOptionalSeconds(context);
        UUID playerId = getPlayerId(context.getSource());
        context.getSource().sendSuccess(() ->
                TicketPerfApi.startAll(
                        context.getSource().getLevel(),
                        seconds,
                        playerId
                ), false);
        return 1;
    }

    private static int listGroups(CommandContext<CommandSourceStack> context,
                                  ChunkGroupSnapshot.PauseMode pauseMode,
                                  String header,
                                  ChunkGroupSnapshot.SortMode sortMode) {
        context.getSource().sendSuccess(() ->
                TicketPerfApi.listGroups(
                        context.getSource().getLevel(),
                        pauseMode,
                        header,
                        sortMode,
                        true,
                        false
                ), false);
        return 1;
    }

    private static int getOptionalSeconds(CommandContext<CommandSourceStack> context) {
        if (context.getNodes().stream().noneMatch(node -> "seconds".equals(node.getNode().getName()))) {
            return defaultAnalyzeSeconds();
        }
        return IntegerArgumentType.getInteger(context, "seconds");
    }

    private static int defaultAnalyzeSeconds() {
        return TicketPerfConfig.defaultAnalyzeSeconds();
    }

    private static int dumpTickets(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                TicketPerfApi.dumpTickets(context.getSource().getLevel(), ChunkGroupSnapshot.PauseMode.ALL, true), true);
        return 1;
    }

    private static UUID getPlayerId(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer sp) {
            return sp.getUUID();
        }
        return null;
    }
}
