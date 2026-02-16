package com.moepus.serverwarashi;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 手动暂停的区块集合持久化数据（按维度存档）。
 */
public final class ManualPauseData extends SavedData {
    private static final String DATA_NAME = "serverwarashi_manual_paused_chunks";
    private static final String TAG_CHUNKS = "paused_chunks";
    private static final SavedData.Factory<ManualPauseData> FACTORY =
            new SavedData.Factory<>(ManualPauseData::new, ManualPauseData::load);

    private final LongOpenHashSet pausedChunks = new LongOpenHashSet();

    /**
     * 获取当前维度的持久化数据实例。
     */
    public static ManualPauseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * 判断指定区块是否被手动暂停。
     */
    public boolean contains(long chunkPos) {
        return pausedChunks.contains(chunkPos);
    }

    /**
     * 记录手动暂停区块坐标。
     */
    public boolean add(long chunkPos) {
        if (pausedChunks.add(chunkPos)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * 移除手动暂停区块坐标。
     */
    public void remove(long chunkPos) {
        if (pausedChunks.remove(chunkPos)) {
            setDirty();
        }
    }

    /**
     * 是否为空集合。
     */
    public boolean isEmpty() {
        return pausedChunks.isEmpty();
    }

    /**
     * 获取区块坐标迭代器。
     */
    public LongIterator iterator() {
        return pausedChunks.iterator();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray(TAG_CHUNKS, pausedChunks.toLongArray());
        return tag;
    }

    /**
     * 从存档加载数据。
     */
    private static ManualPauseData load(CompoundTag tag, HolderLookup.Provider registries) {
        ManualPauseData data = new ManualPauseData();
        long[] values = tag.getLongArray(TAG_CHUNKS);
        for (long value : values) {
            data.pausedChunks.add(value);
        }
        return data;
    }
}
