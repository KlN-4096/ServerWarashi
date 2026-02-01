package com.moepus.serverwarashi.ticketgroup;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Collections;
import java.util.List;

/**
 * Result container for ticket grouping.
 */
public final class TicketGroupingResult {
    private final List<TicketGroupEntry> entries;
    private final List<List<TicketGroupEntry>> buckets;
    private final List<LongOpenHashSet> bucketChunks;

    /**
     * Creates a new grouping result.
     *
     * @param entries      all ticket entries
     * @param buckets      grouped ticket entries
     * @param bucketChunks chunk sets for each group
     */
    public TicketGroupingResult(List<TicketGroupEntry> entries,
                                List<List<TicketGroupEntry>> buckets,
                                List<LongOpenHashSet> bucketChunks) {
        this.entries = Collections.unmodifiableList(entries);
        this.buckets = Collections.unmodifiableList(buckets);
        this.bucketChunks = Collections.unmodifiableList(bucketChunks);
    }

    /**
     * Returns all ticket entries.
     *
     * @return unmodifiable list of ticket entries
     */
    public List<TicketGroupEntry> entries() {
        return entries;
    }

    /**
     * Returns grouped ticket entries.
     *
     * @return unmodifiable list of buckets
     */
    public List<List<TicketGroupEntry>> buckets() {
        return buckets;
    }

    /**
     * Returns chunk sets for each group.
     *
     * @return unmodifiable list of chunk sets
     */
    public List<LongOpenHashSet> bucketChunks() {
        return bucketChunks;
    }

    /**
     * Returns the number of groups.
     *
     * @return group count
     */
    public int groupCount() {
        return buckets.size();
    }
}
