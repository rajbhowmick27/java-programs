package com.practice.javaprograms.sorting;

import java.util.Arrays;

public class RadixSort {

    private static int getMaxElement(int[] arr){
        int mx = -1;
        for(int i=0;i<arr.length;i++){
            mx = Math.max(mx,arr[i]);
        }
        return mx;
    }


    private static void countSort(int[] arr,int n,int exp){
        int[] output = new int[n];

        int[] count = new int[10];
        Arrays.fill(count,0);

        for(int i=0;i<n;i++){
            count[(arr[i]/exp) % 10]++;
        }

        for(int i=1;i<10;i++)
            count[i] += count[i-1];

        for(int i=n-1;i>=0;i--){
            output[count[(arr[i]/exp) % 10]-1] = arr[i];
            count[(arr[i]/exp) % 10]--;
        }

        for(int i=0;i<n;i++)
            arr[i] = output[i];

    }


    private static void radixSort(int[] arr,int n){
        int max = getMaxElement(arr);

        for(int exp=1; max/exp > 0; exp*=10){
            countSort(arr,n,exp);
        }
    }
    public static void main(String[] args){
        int[] arr = new int[]{100,50,25,13,1,2};

        radixSort(arr,arr.length);

        Arrays.stream(arr).forEach(System.out::println);
    }
}
