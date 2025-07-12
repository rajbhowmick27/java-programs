import java.util.*;

public class PartitionCostCalculator {

    public static int[] minMaxPartitionCost(int[] cost, int k) {
        int n = cost.length;
        if (k > n) {
            throw new IllegalArgumentException("Number of partitions cannot exceed the number of elements");
        }

        // Step 1: Build adjacent pair sums
        int[] pairSums = new int[n - 1];
        for (int i = 0; i < n - 1; i++) {
            pairSums[i] = cost[i] + cost[i + 1];
        }

        // Step 2: Use min-heap and max-heap to get k-1 smallest and largest pair sums
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

        for (int val : pairSums) {
            minHeap.offer(val);
            maxHeap.offer(val);
        }

        int minSum = 0, maxSum = 0;
        for (int i = 0; i < k - 1; i++) {
            minSum += minHeap.poll();
            maxSum += maxHeap.poll();
        }

        int base = cost[0] + cost[n - 1];

        int minCost = base + minSum;
        int maxCost = base + maxSum;

        return new int[]{minCost, maxCost};
    }

    // Example usage
    public static void main(String[] args) {
        int[] cost = {1, 2, 3};
        int k = 2;

        int[] result = minMaxPartitionCost(cost, k);
        System.out.println("Min Cost = " + result[0]);
        System.out.println("Max Cost = " + result[1]);
    }
}
