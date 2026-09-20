
package com.si6gma.slipstream.paper.apollo;

public final class VignetteThrottle {
    static final double STEP = 0.05;
    static final long MIN_INTERVAL_TICKS = 4L;
    private static final int NEVER_SENT = -1;
    private int lastBucket = -1;
    private long lastSentTick;

    public static float opacity(double proximity, double maxOpacity) {
        double clamped = Math.max(0.0, Math.min(1.0, proximity));
        return (float)(clamped * maxOpacity);
    }

    static int bucket(float opacity) {
        return (int)Math.round((double)opacity / 0.05);
    }

    public boolean shouldSend(float opacity, long tick) {
        int target = VignetteThrottle.bucket(opacity);
        if (target == this.lastBucket) {
            return false;
        }
        if (this.lastBucket != -1 && tick - this.lastSentTick < 4L) {
            return false;
        }
        this.lastBucket = target;
        this.lastSentTick = tick;
        return true;
    }

    public void reset() {
        this.lastBucket = -1;
        this.lastSentTick = 0L;
    }
}
