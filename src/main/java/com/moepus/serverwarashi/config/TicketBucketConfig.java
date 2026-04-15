package com.moepus.serverwarashi.config;

/**
 * chunkbucket 模块配置门面。
 */
public final class TicketBucketConfig {
    private TicketBucketConfig() {
    }

    /**
     * 读取自动分桶总开关。
     *
     * @return 是否启用自动分桶
     */
    public static boolean enabled() {
        return Config.ENABLED.get();
    }

    /**
     * 设置自动分桶总开关。
     *
     * @param enabled 是否启用自动分桶
     */
    public static void setEnabled(boolean enabled) {
        Config.ENABLED.set(enabled);
    }

    /**
     * 读取是否启用“全部自动暂停”模式。
     *
     * @return 若启用则返回 {@code true}
     */
    public static boolean pauseAll() {
        return Config.PAUSE_ALL_TICKETS.get();
    }

    /**
     * 设置是否启用“全部自动暂停”模式。
     *
     * @param pauseAll 是否全部自动暂停
     */
    public static void setPauseAll(boolean pauseAll) {
        Config.PAUSE_ALL_TICKETS.set(pauseAll);
    }

    /**
     * 读取自动分桶的目标桶大小。
     *
     * @return 目标桶大小
     */
    public static int groupSize() {
        return Config.TICKET_GROUP_SIZE.get();
    }

    /**
     * 设置自动分桶的目标桶大小。
     *
     * @param groupSize 目标桶大小
     */
    public static void setGroupSize(int groupSize) {
        Config.TICKET_GROUP_SIZE.set(groupSize);
    }

    /**
     * 读取自动分桶执行周期。
     *
     * @return 执行周期 tick 数
     */
    public static int runEvery() {
        return Config.RUN_EVERY.get();
    }

    /**
     * 设置自动分桶执行周期。
     *
     * @param runEvery 执行周期 tick 数
     */
    public static void setRunEvery(int runEvery) {
        Config.RUN_EVERY.set(runEvery);
    }

    /**
     * 读取分桶时的邻近合并阈值。
     *
     * @return 邻近阈值
     */
    public static int proximityThreshold() {
        return Config.PROXIMITY_THRESHOLD.get();
    }

    /**
     * 设置分桶时的邻近合并阈值。
     *
     * @param proximityThreshold 邻近阈值
     */
    public static void setProximityThreshold(int proximityThreshold) {
        Config.PROXIMITY_THRESHOLD.set(proximityThreshold);
    }

    /**
     * 将当前自动分桶配置写回配置文件。
     */
    public static void save() {
        Config.SPEC.save();
    }
}
