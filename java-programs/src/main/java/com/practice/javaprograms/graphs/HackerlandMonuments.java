package com.practice.javaprograms.graphs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HackerlandMonuments {

    private static final int MOD = (int) Math.pow(10, 9) + 7;

    public static long countMonumentPlacements(int gNodes, int m, int[] gFrom, int[] gTo) {
        // Build adjacency list for efficient child traversal
        HashMap<Integer, List<Integer>> adjList = new HashMap<>();
        for (int i = 0; i < gNodes - 1; i++) {
            adjList.putIfAbsent(gFrom[i], new ArrayList<>());
            adjList.get(gFrom[i]).add(gTo[i]);
            adjList.putIfAbsent(gTo[i], new ArrayList<>());
            adjList.get(gTo[i]).add(gFrom[i]);
        }

        // Function to calculate valid placements for a subtree (memoization)
        long[][] dp = new long[gNodes][1 << m];
        for (long[] row : dp) {
            java.util.Arrays.fill(row, -1); // Initialize with -1 for uncalculated states
        }

        return dp(1, 0, adjList, dp,m); // Start from root with no monument (mask 0)
    }

    private static long dp(int node, int mask, HashMap<Integer, List<Integer>> adjList, long[][] dp,int m) {
        if (dp[node][mask] != -1) {
            return dp[node][mask];
        }

        dp[node][mask] = 1; // No monument in this node

        for (int child : adjList.getOrDefault(node, new ArrayList<>())) {
            if ((mask & 1) == (getMonumentType(child, mask,m) & 1)) {
                continue; // Skip if same monument type as parent or a common neighbor
            }
            int updatedMask = mask ^ (1 << getMonumentType(child, mask,m)); // Assign next monument type
            dp[node][mask] = (dp[node][mask] + dp(child, updatedMask, adjList, dp,m)) % MOD;
        }

        return dp[node][mask];
    }

    // Function to get a child's monument type from its parent's mask (considering least significant bit)
    private static int getMonumentType(int child, int mask,int m) {
        for (int j = 0; j < m; j++) {
            if ((mask & (1 << j)) != 0) {
                return j;
            }
        }
        return -1; // Should not reach here (child must have a monument type inherited)
    }

    public static void main(String[] args) {
        int gNodes = 4;
        int m = 4;
        int[] gFrom = {1, 1, 2};
        int[] gTo = {2, 3, 4};

        long placements = countMonumentPlacements(gNodes, m, gFrom, gTo);
        System.out.println("Number of valid placements: " + placements);
    }
}
