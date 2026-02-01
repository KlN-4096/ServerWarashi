package com.moepus.serverwarashi;

import com.moepus.serverwarashi.chunkperf.entry.ChunkPerfCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WarashiCommands {
    private static final java.util.function.Predicate<CommandSourceStack> ADMIN_PERMISSION =
            source -> source.hasPermission(2);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = net.minecraft.commands.Commands.literal("warashi")
                .requires(ADMIN_PERMISSION)
                .then(net.minecraft.commands.Commands.literal("enabled")
                        .executes(context -> {
                            context.getSource().sendSuccess(() ->
                                    Component.literal("enabled = " + Config.ENABLED.get()), false);
                            return 1;
                        })
                        .then(net.minecraft.commands.Commands.argument("enabled", BoolArgumentType.bool())
                                .suggests(WarashiCommands::suggestBoolean)
                                .executes(context -> {
                                    Config.ENABLED.set(BoolArgumentType.getBool(context, "enabled"));
                                    Config.SPEC.save();

                                    context.getSource().sendSuccess(() ->
                                            Component.literal("enabled set to " + Config.ENABLED.get()), false);
                                    return 1;
                                })
                        )
                )
                .then(net.minecraft.commands.Commands.literal("ticket")
                        .then(net.minecraft.commands.Commands.literal("pauseAll")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("pauseAll = " + Config.PAUSE_ALL_TICKETS.get()), false);
                                    return 1;
                                })
                                .then(net.minecraft.commands.Commands.argument("pauseAll", BoolArgumentType.bool())
                                        .suggests(WarashiCommands::suggestBoolean)
                                        .executes(context -> {
                                            Config.PAUSE_ALL_TICKETS.set(BoolArgumentType.getBool(context, "pauseAll"));
                                            Config.SPEC.save();

                                            context.getSource().sendSuccess(() ->
                                                    Component.literal("pauseAll set to " + Config.PAUSE_ALL_TICKETS.get()), false);
                                            return 1;
                                        })
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("groupSize")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("groupSize = " + Config.TICKET_GROUP_SIZE.get()), false);
                                    return 1;
                                })
                                .then(net.minecraft.commands.Commands.argument("groupSize", IntegerArgumentType.integer(16, 2048))
                                        .executes(context -> {
                                            Config.TICKET_GROUP_SIZE.set(IntegerArgumentType.getInteger(context, "groupSize"));
                                            Config.SPEC.save();

                                            context.getSource().sendSuccess(() ->
                                                    Component.literal("groupSize set to " + Config.TICKET_GROUP_SIZE.get()), false);
                                            return 1;
                                        })
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("runEvery")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("runEvery = " + Config.RUN_EVERY.get()), false);
                                    return 1;
                                })
                                .then(net.minecraft.commands.Commands.argument("runEvery", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> {
                                            Config.RUN_EVERY.set(IntegerArgumentType.getInteger(context, "runEvery"));
                                            Config.SPEC.save();

                                            context.getSource().sendSuccess(() ->
                                                    Component.literal("runEvery set to " + Config.RUN_EVERY.get()), false);
                                            return 1;
                                        })
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("dump")
                                .executes(context -> DumpTickets(context, false))
                                .then(net.minecraft.commands.Commands.literal("currentWorking")
                                        .executes(context -> DumpTickets(context, true))
                                )
                        )
                );
        ChunkPerfCommands.register(root);
        dispatcher.register(root);
    }

    private static CompletableFuture<Suggestions> suggestBoolean(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
    }

    private static int DumpTickets(CommandContext<CommandSourceStack> context, boolean currentWorking) {
        context.getSource().sendSuccess(() -> ChunkLoadInfo.dumpTickets(context.getSource().getLevel(), currentWorking, true), true);
        return 1;
    }
}
