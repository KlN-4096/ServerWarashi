package com.moepus.serverwarashi.ticketcontrol;

/**
 * Lightweight TPS estimator based on recent tick durations.
 */
final class TpsTracker {
    /**
     * Circular buffer holding recent tick durations.
     */
    private final long[] samples;
    /**
     * Current write index into the sample buffer.
     */
    private int index;
    /**
     * Timestamp recorded at tick start.
     */
    private long lastStartNanos;

    /**
     * Creates a TPS tracker with the provided sample window size.
     *
     * @param windowSize number of samples to keep
     */
    TpsTracker(int windowSize) {
        this.samples = new long[Math.max(1, windowSize)];
    }

    /**
     * Records the start time of a tick.
     */
    void onTickStart() {
        lastStartNanos = System.nanoTime();
    }

    /**
     * Records the end time of a tick and stores the duration.
     */
    void onTickEnd() {
        if (lastStartNanos == 0L) {
            return;
        }
        long duration = System.nanoTime() - lastStartNanos;
        samples[index++ % samples.length] = duration;
    }

    /**
     * Returns an estimated TPS value based on the sample window.
     *
     * @return estimated TPS, clamped to 20
     */
    double getTps() {
        long sum = 0L;
        int count = 0;
        for (long sample : samples) {
            if (sample > 0L) {
                sum += sample;
                count++;
            }
        }
        if (count == 0) {
            return 20.0;
        }
        double avgMs = (sum / (double) count) / 1_000_000.0;
        if (avgMs <= 0.0) {
            return 20.0;
        }
        double tps = 1000.0 / avgMs;
        return Math.min(20.0, tps);
    }
}
