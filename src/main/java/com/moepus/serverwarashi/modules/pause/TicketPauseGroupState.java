package com.moepus.serverwarashi.modules.pause;

/**
 * 分组暂停状态。
 */
public record TicketPauseGroupState(boolean manualPaused, boolean autoPaused, boolean idlePaused) {
    /**
     * 分组暂停原因枚举。
     */
    public enum Reason {
        MANUAL,
        AUTO,
        IDLE
    }

    /**
     * 判断分组是否处于任意暂停状态。
     *
     * @return 只要存在一种暂停原因就返回 {@code true}
     */
    public boolean isPaused() {
        return manualPaused || autoPaused || idlePaused;
    }

    /**
     * 判断该分组当前是否允许执行手动恢复。
     *
     * @return 仅当存在 MANUAL 原因时返回 {@code true}
     */
    public boolean canRestore() {
        return manualPaused;
    }

    /**
     * 判断分组是否包含指定暂停原因。
     *
     * @param reason 目标原因
     * @return 若包含该原因则返回 {@code true}
     */
    public boolean hasReason(Reason reason) {
        return switch (reason) {
            case MANUAL -> manualPaused;
            case AUTO -> autoPaused;
            case IDLE -> idlePaused;
        };
    }

    /**
     * 以可读字符串形式拼接当前分组的暂停原因。
     *
     * @return 原因标签；若无暂停则返回 {@code running}
     */
    public String label() {
        if (!isPaused()) {
            return "running";
        }
        var sj = new java.util.StringJoiner("+");
        if (manualPaused) sj.add("manual");
        if (autoPaused) sj.add("auto");
        if (idlePaused) sj.add("idle");
        return sj.toString();
    }
}
