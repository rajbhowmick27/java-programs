package com.practice.javaprograms.recursion;

import java.util.Arrays;

public class MinCostPath {

    private static int findMinCost(int index,int[] arr){
        if(index == 0)
            return 0;

        int minCost = Integer.MAX_VALUE;
        for(int j=1;index-j>=0;j++){
            int jumpCost = findMinCost(index-j,arr) + Math.abs(arr[index]-arr[index-j]);
            minCost = Math.min(minCost,jumpCost);
        }
        return minCost;
    }
    private static int findMinCostWithMemoization(int index,int[] arr,int[] dp){
        if(index == 0)
            return 0;

        if(dp[index] != -1)
            return dp[index];

        int minCost = Integer.MAX_VALUE;
        for(int j=1;index-j>=0;j++){
            int jumpCost = findMinCost(index-j,arr) + Math.abs(arr[index]-arr[index-j]);
            minCost = Math.min(minCost,jumpCost);
        }
        return dp[index]=minCost;
    }

    private static int findMinCostWithTabulirization(int[] arr){
        int[] dp = new int[arr.length];
        dp[0] = 0;
        for(int i=1;i<arr.length;i++){
            int minJumpCost = Integer.MAX_VALUE;
            for(int j=1;i-j>=0;j++){
                int jumpCost = dp[i-j] + Math.abs(arr[i]-arr[i-j]);
                minJumpCost = Math.min(minJumpCost,jumpCost);
            }
            dp[i] = minJumpCost;
        }
        return dp[arr.length-1];
    }


    public static void main(String[] args){
        int[] arr = new int[]{0,2,5,6,7};
        int[] dp = new int[arr.length];
        Arrays.fill(dp,-1);
        System.out.println("Min Cost : "+findMinCost(arr.length-1,arr));
        System.out.println("Min Cost with memoization : "+findMinCostWithMemoization(arr.length-1,arr,dp));
        System.out.println("Min Cost with tabuliraztion : "+findMinCostWithTabulirization(arr));



    }
}
