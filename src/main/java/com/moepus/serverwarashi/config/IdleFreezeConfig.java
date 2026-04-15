package com.moepus.serverwarashi.config;

public final class IdleFreezeConfig {
    private IdleFreezeConfig() {
    }

    public static boolean enabled() {
        return Config.IDLEFREEZE_ENABLED.get();
    }

    public static int minGroupLoad() {
        return Config.IDLEFREEZE_GROUP_MIN_BE_PLUS_E.get();
    }

    public static int inactiveDays() {
        return Config.IDLEFREEZE_INACTIVE_DAYS.get();
    }

    public static int lastSeenRetentionDays() {
        return Config.IDLEFREEZE_LAST_SEEN_RETENTION_DAYS.get();
    }

    public static int initialScanDelayTicks() {
        return Config.IDLEFREEZE_INITIAL_SCAN_DELAY_SECONDS.get() * 20;
    }
}
