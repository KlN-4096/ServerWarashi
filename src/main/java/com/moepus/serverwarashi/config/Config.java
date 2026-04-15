package com.moepus.serverwarashi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<Boolean> ENABLED = BUILDER
            .define("enabled", false);
    public static final ModConfigSpec.ConfigValue<Boolean> PAUSE_ALL_TICKETS = BUILDER
            .define("pause_all_tickets", false);

    public static final ModConfigSpec.ConfigValue<Integer> TICKET_GROUP_SIZE = BUILDER
            .defineInRange("ticket_group_size", 48, 16, 2048);
    public static final ModConfigSpec.ConfigValue<Integer> RUN_EVERY = BUILDER
            .defineInRange("run_every", 10, 1, 1200);
    public static final ModConfigSpec.ConfigValue<Integer> PROXIMITY_THRESHOLD = BUILDER
            .defineInRange("proximity_threshold", 5, 1, 12);

    public static final ModConfigSpec.ConfigValue<Integer> CHUNKPERF_DEFAULT_ANALYZE_SECONDS = BUILDER
            .defineInRange("chunkperf_default_analyze_seconds", 9999, 1, 86400);

    public static final ModConfigSpec.ConfigValue<Boolean> IDLEFREEZE_ENABLED = BUILDER
            .define("idlefreeze_enabled", true);
    public static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_GROUP_MIN_BE_PLUS_E = BUILDER
            .defineInRange("idlefreeze_group_min_be_plus_e", 1000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_INACTIVE_DAYS = BUILDER
            .defineInRange("idlefreeze_inactive_days", 1, 1, 3650);
    public static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_LAST_SEEN_RETENTION_DAYS = BUILDER
            .defineInRange("idlefreeze_last_seen_retention_days", 3, 1, 3650);
    public static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_INITIAL_SCAN_DELAY_SECONDS = BUILDER
            .defineInRange("idlefreeze_initial_scan_delay_seconds", 180, 0, 3600);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
