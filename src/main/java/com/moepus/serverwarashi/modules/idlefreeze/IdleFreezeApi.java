package com.moepus.serverwarashi.modules.idlefreeze;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * IdleFreeze 模块对外入口。
 * 层级：入口层。
 * 上游：Commands。下游：IdleFreezeService / IdleVisitData。
 */
public final class IdleFreezeApi {
    private IdleFreezeApi() {
    }

    /**
     * 清空当前维度访问记录后，立即执行一次 idlefreeze 初始扫描。
     *
     * @param level 目标维度
     * @return 操作结果消息
     */
    public static Component force(ServerLevel level) {
        IdleVisitData.get(level).clear();
        IdleFreezeService.runInitialScan(level);
        return Component.literal("Idle-freeze force scan finished.")
                .withStyle(ChatFormatting.GREEN);
    }
}
