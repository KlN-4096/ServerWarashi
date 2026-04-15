package com.moepus.serverwarashi.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.moepus.serverwarashi.modules.idlefreeze.IdleFreezeApi;
import net.minecraft.commands.CommandSourceStack;

/**
 * 注册 idlefreeze 模块命令。
 */
public final class IdleFreezeCommands {
    private IdleFreezeCommands() {
    }

    /**
     * 将 {@code /warashi idlefreeze ...} 命令挂载到根节点。
     *
     * @param root 根命令节点
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(net.minecraft.commands.Commands.literal("idlefreeze")
                .then(net.minecraft.commands.Commands.literal("force")
                        .executes(context -> {
                            context.getSource().sendSuccess(() ->
                                    IdleFreezeApi.force(context.getSource().getLevel()), false);
                            return 1;
                        })
                )
        );
    }
}
