package com.practice.javaprograms.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class BuildingMonuments {

    private static int mod = (int)(1e9) + 7;

    private int countWays(int node,int par,int parColor,int m,List<List<Integer>> adj){
        if(adj.get(node).size() == 1 && adj.get(node).get(0) == par){
            if(parColor != -1)
                return m-1;
            else
                return m;
        }

        int totalWays = 1;
        for(int child : adj.get(node)){
            if(child != par){
                int childWays = 0;
                for(int col=0;col<m;col++){
                    if(col != parColor){
                        childWays += countWays(child,node,parColor,m,adj);
                    }
                }
                totalWays = (totalWays % mod * childWays % mod) % mod;
            }
        }

        return totalWays % mod;
    }

    public int buildMonuments(int m,int n,int[] from,int[] to){
        List<List<Integer>> adj = new ArrayList<>();
        for(int i=0;i<=n;i++){
            adj.add(new ArrayList<>());
        }

        for(int i=0;i<from.length;i++) {
            int u = from[i], v = to[i];
            adj.get(u).add(v);
            adj.get(v).add(u);
        }

        return countWays(1,-1,-1,m,adj);
    }

    public static void main(String[] args) throws IOException {
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

        BuildingMonuments buildingMonuments = new BuildingMonuments();
        int res = buildingMonuments.buildMonuments(m,n,from,to);
        System.out.println(res);
    }
}
