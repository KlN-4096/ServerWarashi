package com.moepus.serverwarashi.command;

import com.moepus.serverwarashi.modules.pause.TicketPauseApi;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.moepus.serverwarashi.config.TicketBucketConfig;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 自动分桶模块命令入口。
 * 层级：命令入口层。
 * 上游：WarashiCommands。下游：TicketBucketApi / TicketBucketConfig。
 */
public final class BucketCommands {
    private BucketCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(net.minecraft.commands.Commands.literal("bucket")
                .then(net.minecraft.commands.Commands.literal("enabled")
                        .executes(context -> report(
                                context.getSource(),
                                "bucket",
                                TicketBucketConfig::enabled
                        ))
                        .then(net.minecraft.commands.Commands.argument("enabled", BoolArgumentType.bool())
                                .suggests(BucketCommands::suggestBoolean)
                                .executes(context -> updateBoolean(
                                        context.getSource(),
                                        "bucket",
                                        BoolArgumentType.getBool(context, "enabled"),
                                        TicketBucketConfig::setEnabled,
                                        TicketBucketConfig::save,
                                        () -> {
                                            if (!TicketBucketConfig.enabled()) {
                                                context.getSource().getServer().getAllLevels()
                                                        .forEach(TicketPauseApi::clearAutoPause);
                                            }
                                        },
                                        TicketBucketConfig::enabled
                                ))
                        )
                )
                .then(net.minecraft.commands.Commands.literal("ticket")
                        .then(net.minecraft.commands.Commands.literal("pauseAll")
                                .executes(context -> report(
                                        context.getSource(),
                                        "pauseAll",
                                        TicketBucketConfig::pauseAll
                                ))
                                .then(net.minecraft.commands.Commands.argument("pauseAll", BoolArgumentType.bool())
                                        .suggests(BucketCommands::suggestBoolean)
                                        .executes(context -> updateBoolean(
                                                context.getSource(),
                                                "pauseAll",
                                                BoolArgumentType.getBool(context, "pauseAll"),
                                                TicketBucketConfig::setPauseAll,
                                                TicketBucketConfig::save,
                                                () -> {
                                                },
                                                TicketBucketConfig::pauseAll
                                        ))
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("groupSize")
                                .executes(context -> report(
                                        context.getSource(),
                                        "groupSize",
                                        TicketBucketConfig::groupSize
                                ))
                                .then(net.minecraft.commands.Commands.argument("groupSize", IntegerArgumentType.integer(16, 2048))
                                        .executes(context -> updateInt(
                                                context.getSource(),
                                                "groupSize",
                                                IntegerArgumentType.getInteger(context, "groupSize"),
                                                TicketBucketConfig::setGroupSize,
                                                TicketBucketConfig::save,
                                                TicketBucketConfig::groupSize
                                        ))
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("runEvery")
                                .executes(context -> report(
                                        context.getSource(),
                                        "runEvery",
                                        TicketBucketConfig::runEvery
                                ))
                                .then(net.minecraft.commands.Commands.argument("runEvery", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> updateInt(
                                                context.getSource(),
                                                "runEvery",
                                                IntegerArgumentType.getInteger(context, "runEvery"),
                                                TicketBucketConfig::setRunEvery,
                                                TicketBucketConfig::save,
                                                TicketBucketConfig::runEvery
                                        ))
                                )
                        )
                        .then(net.minecraft.commands.Commands.literal("proximityThreshold")
                                .executes(context -> report(
                                        context.getSource(),
                                        "proximityThreshold",
                                        TicketBucketConfig::proximityThreshold
                                ))
                                .then(net.minecraft.commands.Commands.argument("proximityThreshold", IntegerArgumentType.integer(1, 12))
                                        .executes(context -> updateInt(
                                                context.getSource(),
                                                "proximityThreshold",
                                                IntegerArgumentType.getInteger(context, "proximityThreshold"),
                                                TicketBucketConfig::setProximityThreshold,
                                                TicketBucketConfig::save,
                                                TicketBucketConfig::proximityThreshold
                                        ))
                                )
                        )
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestBoolean(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
    }

    private static int report(CommandSourceStack source, String label, Supplier<?> valueSupplier) {
        source.sendSuccess(() -> Component.literal(label + " = " + valueSupplier.get()), false);
        return 1;
    }

    private static int updateBoolean(CommandSourceStack source,
                                     String label,
                                     boolean value,
                                     Consumer<Boolean> setter,
                                     Runnable saveAction,
                                     Runnable afterSave,
                                     Supplier<?> valueSupplier) {
        setter.accept(value);
        saveAction.run();
        afterSave.run();
        source.sendSuccess(() -> Component.literal(label + " set to " + valueSupplier.get()), false);
        return 1;
    }

    private static int updateInt(CommandSourceStack source,
                                 String label,
                                 int value,
                                 IntConsumer setter,
                                 Runnable saveAction,
                                 Supplier<?> valueSupplier) {
        setter.accept(value);
        saveAction.run();
        source.sendSuccess(() -> Component.literal(label + " set to " + valueSupplier.get()), false);
        return 1;
    }
}
