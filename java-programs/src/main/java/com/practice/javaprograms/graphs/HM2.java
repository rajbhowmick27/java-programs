package com.practice.javaprograms.graphs;

import java.util.*;

public class HM2 {
    static final int MOD = 1000000007;
    static List<List<Integer>> graph;
    static long[][] dp;

    public static void main(String[] args) {
        int m = 4; // Number of types of monuments
        int g_nodes = 4; // Number of cities
        List<Integer> g_from = Arrays.asList(1, 1, 2);
        List<Integer> g_to = Arrays.asList(2, 3, 4);

        // Initialize graph
        graph = new ArrayList();
        for (int i = 0; i <= g_nodes; i++) {
            graph.add(new ArrayList());
        }

        // Build the graph
        for (int i = 0; i < g_from.size(); i++) {
            int from = g_from.get(i);
            int to = g_to.get(i);
            graph.get(from).add(to);
            graph.get(to).add(from);
        }

        // Initialize dp array
        dp = new long[g_nodes + 1][m + 1];
        for (long[] row : dp) {
            Arrays.fill(row, -1);
        }

        // Calculate the number of ways
        long result = dfs(1, 0, m);

        System.out.println(result);
    }

    private static long dfs(int node, int parent, int m) {
        if (dp[node][m] != -1) {
            return dp[node][m];
        }

        long excludeParent = m - 1;
        long includeParent = m - 2;
        long totalWays = excludeParent;
        for (int child : graph.get(node)) {
            if (child == parent) continue;
            long waysForChild = (dfs(child, node, m - 1) * excludeParent) % MOD;
            waysForChild += (dfs(child, node, m - 2) * includeParent) % MOD;
            waysForChild %= MOD;
            totalWays = (totalWays * waysForChild) % MOD;
        }

        dp[node][m] = totalWays;
        return totalWays;
    }
}

