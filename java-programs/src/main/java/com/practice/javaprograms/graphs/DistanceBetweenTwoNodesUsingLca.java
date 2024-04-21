package com.practice.javaprograms.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DistanceBetweenTwoNodesUsingLca {

    private void dfs(int node, int par, int lvl, List<List<Integer>> adj, int[][] lca, int[] level){
        level[node] = lvl;
        lca[node][0] = par;
        for(int adjNode : adj.get(node)){
            if(adjNode != par){
                dfs(adjNode,node,lvl+1,adj,lca,level);
            }
        }
    }

    private int findLca(int a,int b,int maxN,int[] level,int[][] lca){
        if(level[b] < level[a])
            return findLca(b,a,maxN,level,lca);

        int d = level[b] - level[a];

        while(d > 0){
            int i = (int) Math.log(d);
            b = lca[b][i];
            d -= (1<<i);
        }

        if(a == b)
            return a;

        for(int i=maxN;i>=0;i--){
            if(lca[a][i] != -1 && lca[a][i] != lca[b][i]){
                a = lca[a][i];
                b = lca[b][i];
            }
        }

        return lca[a][0];
    }

    private List<Integer> findDistanceBetweenNodes(int n,int[][] edges,int[][] queries){
        int maxN = (int)Math.log((double) n);
        int[][] lca = new int[n+1][maxN+1];
        for(int[] row : lca)
            Arrays.fill(row,-1);
        int[] level = new int[n+1];

        List<List<Integer>> adj = new ArrayList<>();
        for(int i=0;i<=n;i++){
            adj.add(new ArrayList<>());
        }

        for(int i=0;i<edges.length;i++){
            int u = edges[i][0];
            int v = edges[i][1];
            adj.get(u).add(v);
            adj.get(v).add(u);
        }

        dfs(1,-1,0,adj,lca,level);
        for(int j=1;j<=maxN;j++){
            for(int i=1;i<=n;i++){
                if(lca[i][j-1] != -1){
                    int par = lca[i][j-1];
                    lca[i][j] = lca[par][j-1];
                }
            }
        }

        List<Integer> res = new ArrayList<>();

        for(int i=0;i<queries.length;i++){
            int a = queries[i][0];
            int b = queries[i][1];

            int LCA = findLca(a,b,maxN,level,lca);
            res.add(level[a] + level[b] - 2*level[LCA]);
        }

        return res;
    }
    public static void main(String[] args) throws IOException {
        BufferedReader br =
                new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine().trim());
        int m = Integer.parseInt(br.readLine().trim());
        int[][] edges = new int[m][2];
        for(int i=0;i<m;i++) {
            String[] s = br.readLine().trim().split(" ");
            edges[i][0] = Integer.parseInt(s[0]);
            edges[i][1] = Integer.parseInt(s[1]);

        }

        int q = Integer.parseInt(br.readLine().trim());
        int[][] queries = new int[q][2];
        for(int i=0;i<q;i++) {
            String[] s = br.readLine().trim().split(" ");
            queries[i][0] = Integer.parseInt(s[0]);
            queries[i][1] = Integer.parseInt(s[1]);

        }

        DistanceBetweenTwoNodesUsingLca obj = new DistanceBetweenTwoNodesUsingLca();
        List<Integer> res = obj.findDistanceBetweenNodes(n,edges,queries);
        res.forEach(System.out::println);
    }
}
