package com.moepus.serverwarashi.modules.idlefreeze;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 每维度区块最近玩家访问日期。
 */
public final class IdleVisitData extends SavedData {
    private static final String DATA_NAME = "serverwarashi_idle_last_seen";
    private static final String TAG_VERSION = "version";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_LAST_SEEN_DAY = "last_seen_day";
    private static final int VERSION = 1;
    private static final SavedData.Factory<IdleVisitData> FACTORY =
            new SavedData.Factory<>(IdleVisitData::new, IdleVisitData::load);

    private final Long2IntOpenHashMap lastSeenByChunk = new Long2IntOpenHashMap();

    public IdleVisitData() {
        lastSeenByChunk.defaultReturnValue(Integer.MIN_VALUE);
    }

    /**
     * 获取指定维度对应的最近访问记录数据。
     *
     * @param level 目标维度
     * @return 该维度的数据实例
     */
    public static IdleVisitData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * 将区块的最近访问日期更新为指定天数。
     *
     * @param chunkPos 区块坐标
     * @param epochDay 访问日期
     */
    public void markSeen(long chunkPos, int epochDay) {
        int previous = lastSeenByChunk.put(chunkPos, epochDay);
        if (previous != epochDay) {
            setDirty();
        }
    }

    /**
     * 查询区块最近一次被玩家访问的日期。
     *
     * @param chunkPos 区块坐标
     * @return 最近访问的 epoch day；若不存在则返回默认值
     */
    public int getLastSeenDay(long chunkPos) {
        return lastSeenByChunk.get(chunkPos);
    }

    /**
     * 删除早于指定日期的访问记录。
     *
     * @param minDayInclusive 保留的最小日期
     */
    public void pruneBefore(int minDayInclusive) {
        boolean removed = false;
        var iterator = lastSeenByChunk.long2IntEntrySet().iterator();
        while (iterator.hasNext()) {
            Long2IntMap.Entry entry = iterator.next();
            if (entry.getIntValue() >= minDayInclusive) {
                continue;
            }
            iterator.remove();
            removed = true;
        }
        if (removed) {
            setDirty();
        }
    }

    /**
     * 清空当前维度全部最近访问记录。
     */
    public void clear() {
        if (lastSeenByChunk.isEmpty()) {
            return;
        }
        lastSeenByChunk.clear();
        setDirty();
    }

    /**
     * 将最近访问记录写入维度存档。
     *
     * @param tag 输出标签
     * @param registries 注册表访问器
     * @return 写入后的标签
     */
    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(TAG_VERSION, VERSION);
        int size = lastSeenByChunk.size();
        long[] chunks = new long[size];
        int[] lastSeen = new int[size];
        int index = 0;
        for (var entry : lastSeenByChunk.long2IntEntrySet()) {
            chunks[index] = entry.getLongKey();
            lastSeen[index] = entry.getIntValue();
            index++;
        }
        tag.putLongArray(TAG_CHUNKS, chunks);
        tag.putIntArray(TAG_LAST_SEEN_DAY, lastSeen);
        return tag;
    }

    /**
     * 从维度存档恢复最近访问记录。
     *
     * @param tag 输入标签
     * @param registries 注册表访问器
     * @return 恢复后的数据实例
     */
    private static IdleVisitData load(CompoundTag tag, HolderLookup.Provider registries) {
        IdleVisitData data = new IdleVisitData();
        long[] chunks = tag.getLongArray(TAG_CHUNKS);
        int[] lastSeen = tag.getIntArray(TAG_LAST_SEEN_DAY);
        int size = Math.min(chunks.length, lastSeen.length);
        for (int i = 0; i < size; i++) {
            data.lastSeenByChunk.put(chunks[i], lastSeen[i]);
        }
        return data;
    }
}
