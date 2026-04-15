package com.moepus.serverwarashi.modules.bucket;

import com.moepus.serverwarashi.config.TicketBucketConfig;

import java.util.ArrayList;
import java.util.List;

public class TicketMorton {    /**
 * 将二维 chunk 坐标编码为 Morton 序。
 *
 * @param x chunk X
 * @param z chunk Z
 * @return Morton 编码结果
 */
    static long morton2D(int x, int z) {
        long xx = Integer.toUnsignedLong(x);
        long zz = Integer.toUnsignedLong(z);
        return interleaveBits(xx) | (interleaveBits(zz) << 1);
    }

    /**
     * 对无符号坐标执行 bit interleave。
     *
     * @param x 原始坐标
     * @return 交错后的比特序列
     */
    private static long interleaveBits(long x) {
        x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
        x = (x | (x << 8)) & 0x00FF00FF00FF00FFL;
        x = (x | (x << 4)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x << 2)) & 0x3333333333333333L;
        x = (x | (x << 1)) & 0x5555555555555555L;
        return x;
    }

    /**
     * 将 ticket 按 Morton 顺序和邻近阈值切分成多个桶。
     *
     * @param allTickets 全部候选 ticket
     * @return 切分后的桶列表
     */
    static List<List<TicketBucketService.TicketEntry>> divideChunkBuckets(List<TicketBucketService.TicketEntry> allTickets) {
        int targetBucketSize = TicketBucketConfig.groupSize();
        int maxBucketSize = targetBucketSize * 2;

        List<List<TicketBucketService.TicketEntry>> buckets = new ArrayList<>();
        List<TicketBucketService.TicketEntry> current = new ArrayList<>();

        for (TicketBucketService.TicketEntry entry : allTickets) {
            if (shouldAppendToCurrentBucket(current, entry, targetBucketSize, maxBucketSize)) {
                current.add(entry);
                continue;
            }

            buckets.add(current);
            current = new ArrayList<>();
            current.add(entry);
        }
        if (!current.isEmpty()) {
            buckets.add(current);
        }
        return buckets;
    }

    private static boolean shouldAppendToCurrentBucket(List<TicketBucketService.TicketEntry> current,
                                                TicketBucketService.TicketEntry entry,
                                                int targetBucketSize,
                                                int maxBucketSize) {
        if (current.size() < targetBucketSize) {
            return true;
        }
        if (current.size() >= maxBucketSize) {
            return false;
        }
        long diff = entry.morton() - current.get(current.size() - 1).morton();
        return diff <= TicketBucketConfig.proximityThreshold();
    }
}
