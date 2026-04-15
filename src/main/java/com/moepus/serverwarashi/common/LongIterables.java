package com.moepus.serverwarashi.common;

import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.Iterator;

public final class LongIterables {
    private LongIterables() {
    }

    public static Iterable<Long> from(LongIterator iterator) {
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Long next() {
                return iterator.nextLong();
            }
        };
    }
}
