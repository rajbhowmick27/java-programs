package com.practice.javaprograms.graphs;

import java.util.ArrayList;
import java.util.List;

public class DisjointSet {
    private List<Integer> rank = new ArrayList<>();
    private List<Integer> parent = new ArrayList<>();

    private List<Integer> size = new ArrayList<>();

    public DisjointSet(int n){
        for(int i=0;i<=n;i++){
            rank.add(0);
            parent.add(i);
            size.add(1);
        }
    }

    public int findParent(int node){
        if(node == parent.get(node))
            return node;

        int ulp = findParent(parent.get(node));
        parent.set(node,ulp);
        return parent.get(node);
    }

    public void unionByRank(int u,int v){
        int ulp_u = findParent(u);
        int ulp_v = findParent(v);

        if(ulp_u == ulp_v)
            return;

        if(rank.get(ulp_u) < rank.get(ulp_v)){
            parent.set(ulp_u,ulp_v);
        }
        else if(rank.get(ulp_v) < rank.get(ulp_u)){
            parent.set(ulp_v,ulp_u);
        }
        else{
            // v-> u
            parent.set(ulp_v,ulp_u);
            int rankU = rank.get(ulp_u);
            rank.set(ulp_u,rankU+1);
        }
    }

    public void unionBySize(int u,int v){
        int ulp_u = findParent(u);
        int ulp_v = findParent(v);

        if(ulp_u == ulp_v)
            return;
        if(size.get(ulp_u) < size.get(ulp_v)){
            parent.set(ulp_u,ulp_v);
            size.set(ulp_v,size.get(ulp_v) + size.get(ulp_u));
        }
        else{
            parent.set(ulp_v,ulp_u);
            size.set(ulp_u,size.get(ulp_u) + size.get(ulp_v));
        }
    }

    public static void main(String[] args) {
        DisjointSet ds = new DisjointSet(7);
        ds.unionByRank(1,2);
        ds.unionByRank(2,3);
        ds.unionByRank(4,5);
        ds.unionByRank(6,7);
        ds.unionByRank(5,6);

        if(ds.findParent(3) == ds.findParent(7)){
            System.out.println("Same");
        }
        else{
            System.out.println("Not Same");
        }

        ds.unionByRank(3,7);

        if(ds.findParent(3) == ds.findParent(7)){
            System.out.println("Same");
        }
        else{
            System.out.println("Not Same");
        }

        DisjointSet ds2 = new DisjointSet(7);
        ds2.unionBySize(1,2);
        ds2.unionBySize(2,3);
        ds2.unionBySize(4,5);
        ds2.unionBySize(6,7);
        ds2.unionBySize(5,6);

        if(ds2.findParent(3) == ds2.findParent(7)){
            System.out.println("Same");
        }
        else{
            System.out.println("Not Same");
        }

        ds2.unionBySize(3,7);

        if(ds2.findParent(3) == ds2.findParent(7)){
            System.out.println("Same");
        }
        else{
            System.out.println("Not Same");
        }
    }
}
