package com.moepus.serverwarashi.modules.performance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * ChunkPerf 热路径钩子：在非分析状态下尽量直接回落到原始调用。
 */
public final class TicketPerfMixinHooks {
    private TicketPerfMixinHooks() {
    }

    public static <T extends Entity> Consumer<T> wrapEntityTickConsumer(ServerLevel level, Consumer<T> original, T entity) {
        if (!TicketPerfRuntime.hasActiveSession()) {
            return original;
        }

        BlockPos entityPos = entity.blockPosition();
        long sessionId = TicketPerfRuntime.resolveTrackSessionId(level, entityPos);
        if (sessionId < 0L) {
            return original;
        }

        return new ProfilingEntityConsumer<>(level, original, entity, entityPos, sessionId);
    }

    public static Iterator<TickingBlockEntity> wrapBlockEntityIterator(Level level, Iterator<TickingBlockEntity> original) {
        if (level.isClientSide || !TicketPerfRuntime.hasActiveSession()) {
            return original;
        }
        return new ProfilingBlockEntityIterator(level, original);
    }

    public static void profileChunkTick(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        if (!TicketPerfRuntime.hasActiveSession()) {
            level.tickChunk(chunk, randomTickSpeed);
            return;
        }

        ChunkPos pos = chunk.getPos();
        long sessionId = TicketPerfRuntime.resolveTrackSessionId(level, pos.getWorldPosition());
        if (sessionId < 0L) {
            level.tickChunk(chunk, randomTickSpeed);
            return;
        }

        long start = System.nanoTime();
        level.tickChunk(chunk, randomTickSpeed);
        long duration = System.nanoTime() - start;
        TicketPerfRuntime.onChunkTick(level, pos, duration, sessionId);
    }

    private static String formatEntityLabel(String entityType, String entityId, BlockPos pos) {
        String shortUuid = entityId.length() > 8 ? entityId.substring(0, 8) : entityId;
        return entityType + "#" + shortUuid + "@(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String formatBlockEntityLabel(String blockEntityType, BlockPos pos) {
        return blockEntityType + "@(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private record ProfilingEntityConsumer<T extends Entity>(ServerLevel level, Consumer<T> delegate, T entity,
                                                             BlockPos entityPos,
                                                             long sessionId) implements Consumer<T> {
        @Override
            public void accept(T value) {
                long start = System.nanoTime();
                delegate.accept(value);
                long duration = System.nanoTime() - start;
                String entityType = entity.getType().toString();
                String entityId = entity.getUUID().toString();
                String entityLabel = formatEntityLabel(entityType, entityId, entityPos);
                TicketPerfRuntime.onEntityTick(
                        level,
                        entityPos,
                        entityType,
                        entityId,
                        entityLabel,
                        duration,
                        false,
                        sessionId
                );
            }
        }

    private record ProfilingBlockEntityIterator(Level level,
                                                Iterator<TickingBlockEntity> delegate) implements Iterator<TickingBlockEntity> {

        @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public TickingBlockEntity next() {
                TickingBlockEntity next = delegate.next();
                BlockPos pos = next.getPos();

                long sessionId = TicketPerfRuntime.resolveTrackSessionId(level, pos);
                if (sessionId < 0L) {
                    return next;
                }

                return new ProfilingBlockEntity(level, next, pos, sessionId);
            }

            @Override
            public void remove() {
                delegate.remove();
            }
        }

    private record ProfilingBlockEntity(Level level, TickingBlockEntity delegate, BlockPos pos,
                                        long sessionId) implements TickingBlockEntity {

        @Override
            public void tick() {
                long start = System.nanoTime();
                delegate.tick();
                long duration = System.nanoTime() - start;
                String blockEntityType = delegate.getType();
                String sourceId = Long.toString(pos.asLong());
                String sourceLabel = formatBlockEntityLabel(blockEntityType, pos);
                TicketPerfRuntime.onEntityTick(
                        level,
                        pos,
                        blockEntityType,
                        sourceId,
                        sourceLabel,
                        duration,
                        true,
                        sessionId
                );
            }

            @Override
            public boolean isRemoved() {
                return delegate.isRemoved();
            }

            @Override
            public @NotNull BlockPos getPos() {
                return delegate.getPos();
            }

            @Override
            public @NotNull String getType() {
                return delegate.getType();
            }
        }
}
