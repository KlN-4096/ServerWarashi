package com.moepus.serverwarashi.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.moepus.serverwarashi.modules.pause.TicketPauseApi;
import com.moepus.serverwarashi.modules.pause.TicketPauseGroupState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * ChunkPause 命令入口。
 * 层级：命令入口层。
 * 上游：WarashiCommands。下游：TicketPauseApi。
 */
public final class PauseCommands {
    private PauseCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(net.minecraft.commands.Commands.literal("pause")
                .then(net.minecraft.commands.Commands.literal("list")
                        .then(net.minecraft.commands.Commands.literal("lowered")
                                .executes(PauseCommands::listLoweredGroups)
                                .then(net.minecraft.commands.Commands.literal("manual")
                                        .executes(context -> listLoweredGroups(context, TicketPauseGroupState.Reason.MANUAL)))
                                .then(net.minecraft.commands.Commands.literal("auto")
                                        .executes(context -> listLoweredGroups(context, TicketPauseGroupState.Reason.AUTO)))
                                .then(net.minecraft.commands.Commands.literal("idle")
                                        .executes(context -> listLoweredGroups(context, TicketPauseGroupState.Reason.IDLE)))
                        )
                )
                .then(net.minecraft.commands.Commands.literal("group")
                        .then(net.minecraft.commands.Commands.literal("lower")
                                .then(net.minecraft.commands.Commands.argument("group", IntegerArgumentType.integer(0))
                                        .executes(PauseCommands::lowerGroup)
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("restore")
                                .then(net.minecraft.commands.Commands.literal("here")
                                        .executes(PauseCommands::restoreGroupHere)
                                )
                                .then(net.minecraft.commands.Commands.argument("group", IntegerArgumentType.integer(0))
                                        .executes(PauseCommands::restoreGroup)
                                )
                        )
                )
        );
    }

    private static int lowerGroup(CommandContext<CommandSourceStack> context) {
        int groupIndex = IntegerArgumentType.getInteger(context, "group");
        context.getSource().sendSuccess(() ->
                TicketPauseApi.lower(context.getSource().getLevel(), groupIndex), false);
        return 1;
    }

    private static int restoreGroup(CommandContext<CommandSourceStack> context) {
        int groupIndex = IntegerArgumentType.getInteger(context, "group");
        context.getSource().sendSuccess(() ->
                TicketPauseApi.restore(context.getSource().getLevel(), groupIndex), false);
        return 1;
    }

    private static int restoreGroupHere(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        context.getSource().sendSuccess(() ->
                TicketPauseApi.restoreHere(context.getSource().getLevel(), pos), false);
        return 1;
    }

    private static int listLoweredGroups(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                TicketPauseApi.listLoweredGroups(
                        context.getSource().getLevel(),
                        "Paused ticket groups:",
                        null
                ), false);
        return 1;
    }

    private static int listLoweredGroups(CommandContext<CommandSourceStack> context,
                                         TicketPauseGroupState.Reason reasonFilter) {
        context.getSource().sendSuccess(() ->
                TicketPauseApi.listLoweredGroups(
                        context.getSource().getLevel(),
                        "Paused ticket groups (" + reasonFilter.name().toLowerCase(Locale.ROOT) + "):",
                        reasonFilter
                ), false);
        return 1;
    }
}
