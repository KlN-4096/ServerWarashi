package com.moepus.serverwarashi.config;

/**
 * performance 模块配置门面。
 */
public final class TicketPerfConfig {
    private TicketPerfConfig() {
    }

    public static int defaultAnalyzeSeconds() {
        return Config.CHUNKPERF_DEFAULT_ANALYZE_SECONDS.get();
    }
}
