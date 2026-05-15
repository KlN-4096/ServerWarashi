package com.moepus.serverwarashi.modules.idlefreeze;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 每维度因 IDLE 规则被冻结的分组集合。
 */
public final class IdlePauseData extends SavedData {
    private static final String DATA_NAME = "serverwarashi_idle_paused_groups";
    private static final String TAG_VERSION = "version";
    private static final String TAG_GROUPS = "groups";
    private static final String TAG_CHUNKS = "chunks";
    private static final int VERSION = 1;
    private static final SavedData.Factory<IdlePauseData> FACTORY =
            new SavedData.Factory<>(IdlePauseData::new, IdlePauseData::load);

    private final List<FrozenGroup> frozenGroups = new ArrayList<>();

    /**
     * 判断当前维度是否没有任何被空闲冻结的分组。
     *
     * @return 若为空则返回 {@code true}
     */
    public boolean isEmpty() {
        return frozenGroups.isEmpty();
    }

    /**
     * 返回当前维度已记录的全部冻结分组快照。
     *
     * @return 不可修改的冻结分组列表
     */
    public List<FrozenGroup> groups() {
        return Collections.unmodifiableList(frozenGroups);
    }

    /**
     * 获取指定维度对应的空闲冻结持久化数据。
     *
     * @param level 目标维度
     * @return 该维度的数据实例
     */
    public static IdlePauseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * 记录一组新的空闲冻结区块；若与已有分组重叠，则用新分组覆盖旧分组。
     *
     * @param chunks 需要冻结的区块集合
     */
    public void addGroup(Set<Long> chunks) {
        FrozenGroup existing = findGroupOverlapping(chunks);
        if (existing != null) {
            frozenGroups.remove(existing);
        }
        frozenGroups.add(FrozenGroup.of(chunks));
        setDirty();
    }

    /**
     * 删除包含指定区块的冻结分组。
     *
     * @param chunkPos 组内任意区块坐标
     * @return 被移除的冻结分组；若不存在则返回 {@code null}
     */
    public FrozenGroup removeGroupContaining(long chunkPos) {
        FrozenGroup group = findGroupContaining(chunkPos);
        if (group != null) {
            frozenGroups.remove(group);
            setDirty();
        }
        return group;
    }

    /**
     * 直接移除指定的冻结分组实例。
     *
     * @param group 要移除的分组
     * @return 若成功移除则返回 {@code true}
     */
    public boolean removeGroup(FrozenGroup group) {
        if (frozenGroups.remove(group)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * 查找包含指定区块的冻结分组。
     *
     * @param chunkPos 要查询的区块坐标
     * @return 命中的冻结分组；若不存在则返回 {@code null}
     */
    public FrozenGroup findGroupContaining(long chunkPos) {
        for (FrozenGroup group : frozenGroups) {
            if (group.contains(chunkPos)) {
                return group;
            }
        }
        return null;
    }

    /**
     * 查找与给定区块集合存在交集的冻结分组。
     *
     * @param chunks 需要匹配的区块集合
     * @return 首个有交集的冻结分组；若不存在则返回 {@code null}
     */
    public FrozenGroup findGroupOverlapping(Set<Long> chunks) {
        for (FrozenGroup group : frozenGroups) {
            if (group.overlaps(chunks)) {
                return group;
            }
        }
        return null;
    }

    /**
     * 将冻结分组写入维度存档。
     *
     * @param tag 输出标签
     * @param registries 注册表访问器
     * @return 写入后的标签
     */
    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(TAG_VERSION, VERSION);
        ListTag groupsTag = new ListTag();
        for (FrozenGroup group : frozenGroups) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putLongArray(TAG_CHUNKS, group.toLongArray());
            groupsTag.add(groupTag);
        }
        tag.put(TAG_GROUPS, groupsTag);
        return tag;
    }

    /**
     * 从维度存档恢复冻结分组列表。
     *
     * @param tag 输入标签
     * @param registries 注册表访问器
     * @return 恢复后的数据实例
     */
    private static IdlePauseData load(CompoundTag tag, HolderLookup.Provider registries) {
        IdlePauseData data = new IdlePauseData();
        ListTag groups = tag.getList(TAG_GROUPS, Tag.TAG_COMPOUND);
        for (Tag rawGroupTag : groups) {
            CompoundTag groupTag = (CompoundTag) rawGroupTag;
            data.frozenGroups.add(FrozenGroup.of(groupTag.getLongArray(TAG_CHUNKS)));
        }
        return data;
    }

    /**
     * 一组因为空闲规则而被冻结的区块集合。
     *
     * @param chunks 组内全部区块
     */
    public record FrozenGroup(LongOpenHashSet chunks) {
        /**
         * 从区块集合构建冻结分组副本。
         *
         * @param chunks 区块集合
         * @return 新的冻结分组
         */
        public static FrozenGroup of(Set<Long> chunks) {
            return new FrozenGroup(new LongOpenHashSet(chunks));
        }

        /**
         * 从存档读取出的区块数组构建冻结分组。
         *
         * @param chunks 区块数组
         * @return 新的冻结分组
         */
        public static FrozenGroup of(long[] chunks) {
            return new FrozenGroup(new LongOpenHashSet(chunks));
        }

        /**
         * 判断分组是否包含指定区块。
         *
         * @param chunkPos 区块坐标
         * @return 若包含则返回 {@code true}
         */
        public boolean contains(long chunkPos) {
            return chunks.contains(chunkPos);
        }

        /**
         * 判断当前分组是否与另一组区块有交集。
         *
         * @param other 另一组区块
         * @return 若存在交集则返回 {@code true}
         */
        public boolean overlaps(Set<Long> other) {
            for (long chunkPos : other) {
                if (chunks.contains(chunkPos)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 导出组内区块数组。
         *
         * @return 区块数组副本
         */
        public long[] toLongArray() {
            return chunks.toLongArray();
        }
    }
}
