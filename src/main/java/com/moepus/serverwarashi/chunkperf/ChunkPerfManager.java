package com.moepus.serverwarashi.chunkperf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Public entry point that wraps the internal chunk performance module.
 * External callers should use this manager instead of accessing internal classes.
 */
public final class ChunkPerfManager {
    /**
     * Singleton manager instance used for static delegation.
     */
    private static final ChunkPerfManager INSTANCE = new ChunkPerfManager();
    /**
     * Internal module that performs the chunk performance logic.
     */
    private final ChunkPerfModule module = new ChunkPerfModule();

    /**
     * Starts a performance session for a group index.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to track
     * @return a status component describing the result
     */
    public static Component start(ServerLevel level, int groupIndex) {
        return INSTANCE.module.start(level, groupIndex);
    }

    /**
     * Starts a performance session for a group index with an optional ignore33 flag.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to track
     * @param ignore33   whether to ignore tickets already at level 33
     * @return a status component describing the result
     */
    public static Component start(ServerLevel level, int groupIndex, boolean ignore33) {
        return INSTANCE.module.start(level, groupIndex, ignore33);
    }

    /**
     * Starts a timed session for a group index and auto-reports the result.
     *
     * @param source      the command source
     * @param groupIndex  the group index to track
     * @param ignore33    whether to ignore tickets already at level 33
     * @param durationSec session duration in seconds
     * @return a status component describing the result
     */
    public static Component start(CommandSourceStack source, int groupIndex, boolean ignore33, int durationSec) {
        var player = source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null;
        return INSTANCE.module.start(
                source.getLevel(),
                groupIndex,
                ignore33,
                durationSec,
                player == null ? null : player.getUUID()
        );
    }

    /**
     * Lowers the ticket level for a group index.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to lower
     * @return a status component describing the result
     */
    public static Component lower(ServerLevel level, int groupIndex) {
        return INSTANCE.module.lower(level, groupIndex);
    }

    /**
     * Lowers the ticket level for a group index with an optional ignore33 flag.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to lower
     * @param ignore33   whether to ignore tickets already at level 33
     * @return a status component describing the result
     */
    public static Component lower(ServerLevel level, int groupIndex, boolean ignore33) {
        return INSTANCE.module.lower(level, groupIndex, ignore33);
    }

    /**
     * Restores the ticket level for a lowered group index.
     *
     * @param level      the server level to analyze
     * @param groupIndex the group index to restore
     * @return a status component describing the result
     */
    public static Component restore(ServerLevel level, int groupIndex) {
        return INSTANCE.module.restore(level, groupIndex);
    }

    /**
     * Stops the current session and returns a report.
     *
     * @param level the server level to report on
     * @return a report component
     */
    public static Component stop(ServerLevel level) {
        return INSTANCE.module.stop(level);
    }

    /**
     * Lists available groups for the level.
     *
     * @param level the server level to inspect
     * @return a component describing the groups
     */
    public static Component listGroups(ServerLevel level) {
        return INSTANCE.module.listGroups(level);
    }

    /**
     * Lists available groups for the level with an optional ignore33 flag.
     *
     * @param level    the server level to inspect
     * @param ignore33 whether to ignore tickets already at level 33
     * @return a component describing the groups
     */
    public static Component listGroups(ServerLevel level, boolean ignore33) {
        return INSTANCE.module.listGroups(level, ignore33);
    }

    /**
     * Advances the auto-analysis scheduler.
     *
     * @param server minecraft server instance
     */
    public static void onServerTick(MinecraftServer server) {
        INSTANCE.module.onServerTick(server);
    }

    /**
     * Checks if the given block position should be tracked.
     *
     * @param level the level hosting the block entity
     * @param pos   the block position
     * @return true if tracking is active and the chunk is in the target group
     */
    public static boolean shouldTrack(Level level, BlockPos pos) {
        return INSTANCE.module.shouldTrack(level, pos);
    }

    /**
     * Records a block entity tick measurement.
     *
     * @param level         the level hosting the block entity
     * @param pos           the block position
     * @param type          block entity type identifier
     * @param durationNanos duration in nanoseconds
     */
    public static void onBlockEntityTick(Level level, BlockPos pos, String type, long durationNanos) {
        INSTANCE.module.onBlockEntityTick(level, pos, type, durationNanos);
    }

    /**
     * Records an entity tick measurement.
     *
     * @param level         the level hosting the entity
     * @param pos           the entity position
     * @param type          entity type identifier
     * @param durationNanos duration in nanoseconds
     */
    public static void onEntityTick(Level level, BlockPos pos, String type, long durationNanos) {
        INSTANCE.module.onEntityTick(level, pos, type, durationNanos);
    }

    /**
     * Records a chunk tick measurement.
     *
     * @param level         the level hosting the chunk
     * @param pos           the chunk position
     * @param durationNanos duration in nanoseconds
     */
    public static void onChunkTick(Level level, net.minecraft.world.level.ChunkPos pos, long durationNanos) {
        INSTANCE.module.onChunkTick(level, pos, durationNanos);
    }

    /**
     * Utility class.
     */
    private ChunkPerfManager() {
    }
}
