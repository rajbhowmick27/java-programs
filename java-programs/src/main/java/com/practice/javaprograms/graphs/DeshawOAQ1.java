package com.practice.javaprograms.graphs;

import java.util.*;

public class DeshawOAQ1 {

    private static final int MOD = 1000000007;

    public static int getTotalWays(int m, int g_nodes, int[] g_from, int[] g_to) {
        // Create an adjacency list for the tree
        List<List<Integer>> graph = new ArrayList<>(g_nodes + 1);
        for (int i = 0; i <= g_nodes; i++) {
            graph.add(new ArrayList<>());
        }

        // Build the graph from the edges
        for (int i = 0; i < g_from.length; i++) {
            graph.get(g_from[i]).add(g_to[i]);
            graph.get(g_to[i]).add(g_from[i]);
        }

        // Array to store the number of ways to assign types
        long[] dp = new long[g_nodes + 1];
        boolean[] visited = new boolean[g_nodes + 1];

        // Perform DFS to fill the dp array and count ways
        dfs(graph, 1, m, visited, dp);

        return (int) (dp[1] % MOD);
    }

    private static void dfs(List<List<Integer>> graph, int node, int m, boolean[] visited, long[] dp) {
        visited[node] = true;

        // Initialize ways for the current node
        long ways = m;

        // Get neighbors
        List<Integer> neighbors = graph.get(node);
        int degree = neighbors.size();

        if (degree > 0) {
            ways = (ways * (m - 1)) % MOD; // First neighbor can have (m - 1) choices

            // Each remaining neighbor can have (m - 1 - i) choices
            for (int i = 1; i < degree; i++) {
                ways = (ways * (m - 1 - i)) % MOD;
            }
        }

        dp[node] = ways; // Store the number of ways for the current node

        // Visit all neighbors
        for (int neighbor : neighbors) {
            if (!visited[neighbor]) {
                dfs(graph, neighbor, m, visited, dp);
            }
        }
    }

    public static void main(String[] args) {
        // Sample Input
        int m = 3;
        int g_nodes = 4;
        int[] g_from = {1, 2, 3};
        int[] g_to = {2, 3, 4};


        // Output the result
        System.out.println(getTotalWays(m, g_nodes, g_from, g_to));  // Expected Output: 6
    }
}
