package com.moepus.serverwarashi.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Warashi 根命令装配器。
 * 层级：命令装配层。
 * 上游：Serverwarashi。下游：各命令入口。
 */
public class WarashiCommands {
    private static final java.util.function.Predicate<CommandSourceStack> ADMIN_PERMISSION =
            source -> source.hasPermission(2);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = net.minecraft.commands.Commands.literal("warashi")
                .requires(ADMIN_PERMISSION);
        BucketCommands.register(root);
        PerfCommands.register(root);
        IdleFreezeCommands.register(root);
        dispatcher.register(root);
    }
}
