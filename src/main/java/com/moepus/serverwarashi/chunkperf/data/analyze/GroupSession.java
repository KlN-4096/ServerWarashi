package com.moepus.serverwarashi.chunkperf.data.analyze;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 分组性能会话公共字段。
 */
public abstract class GroupSession {
    public final long id;
    public final ResourceKey<Level> dimension;
    public final long startedAtNanos;
    public long serverTickCount;

    protected GroupSession(long id, ResourceKey<Level> dimension) {
        this.id = id;
        this.dimension = dimension;
        this.startedAtNanos = System.nanoTime();
        this.serverTickCount = 0L;
    }
}
