package com.practice.javaprograms.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MonumentsBuilding {
    static final int MOD = 1000000007;

    public static int countWaysToBuildMonuments(int m,int n, int[] from,int[] to) {
        long[][] dp = new long[n + 1][m + 1];
        for (int i = 1; i <= m; i++) {
            dp[1][i] = 1;
        }

        for (int i = 2; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                dp[i][j] = 1;
                for (int edgeNo=0;edgeNo<n-1;edgeNo++) {
                    int u = from[edgeNo];
                    int v = to[edgeNo];
                    if (u == i || v == i) {
                        dp[i][j] = (dp[i][j] * (m - dp[Math.min(u, v)][j])) % MOD;
                    }
                }
            }
        }

        long totalWays = 0;
        for (int i = 1; i <= m; i++) {
            totalWays = (totalWays + dp[n][i]) % MOD;
        }

        return (int) totalWays;
    }

    public static int countWaysToBuildMonumentsWithDfs(int m,int n, int[] from,int[] to) {
        List<List<Integer>> tree = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            tree.add(new ArrayList<>());
        }

        for (int i=0;i<n-1;i++) {
            int u = from[i];
            int v = to[i];
            tree.get(u).add(v);
            tree.get(v).add(u);
        }

        long[][] dp = new long[n + 1][m + 1];
        dfs(1, 0,dp,tree);

        long totalWays = 0;
        for (int i = 1; i <= m; i++) {
            totalWays = (totalWays + dp[1][i]) % MOD;
        }

        return (int) totalWays;
    }

    private static void dfs(int node, int parent,long[][] dp,List<List<Integer>> tree) {
        dp[node][1] = 1;
        for (int child : tree.get(node)) {
            if (child == parent) {
                continue;
            }
            dfs(child, node,dp,tree);
        }

        for (int color = 2; color <= dp[node].length - 1; color++) {
            long product = 1;
            for (int child : tree.get(node)) {
                if (child == parent) {
                    continue;
                }
                product = (product * (dp[child][color - 1] + dp[child][color])) % MOD;
            }
            dp[node][color] = product;
        }
    }

    public static void main(String[] args) throws IOException {
//        int g = 5; // Number of cities
//        int m = 3; // Number of types of monuments
//        int[][] edges = {{1, 2}, {2, 3}, {2, 4}, {4, 5}}; // Edge connections between cities

        BufferedReader br =
                new BufferedReader(new InputStreamReader(System.in));
        int m = Integer.parseInt(br.readLine().trim());
        int n = Integer.parseInt(br.readLine().trim());
        int[] from = new int[n-1];
        int[] to = new int[n-1];
        for(int i=0;i<n-1;i++) {
            String[] s = br.readLine().trim().split(" ");
            from[i] = Integer.parseInt(s[0]);
            to[i] = Integer.parseInt(s[1]);

        }

//        int ways = countWaysToBuildMonuments(m,n,from,to);
        int ways = countWaysToBuildMonumentsWithDfs(m,n,from,to);
        System.out.println("Number of ways to build monuments in all cities: " + ways);
    }
}
