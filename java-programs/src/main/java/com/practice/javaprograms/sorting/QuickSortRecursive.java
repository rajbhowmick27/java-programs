package com.practice.javaprograms.sorting;

import java.util.Arrays;

public class QuickSortRecursive {
    private static void swap(int arr[],int i,int j){
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }


    private static int partition(int arr[],int low,int high){
        int pivot = arr[high];

        int prevIndexOfPivot = low - 1;

        for(int i=low;i<=high;i++){
            if(arr[i] < pivot){
                prevIndexOfPivot++; // increment prevIndexOfPivot
                swap(arr,prevIndexOfPivot,i); // swap arr[prevIndexOfPivot] with arr[i]
            }
        }

        // finally put the pivot in it's correct position
        swap(arr,prevIndexOfPivot+1,high);

        return (prevIndexOfPivot+1);
    }

    private static void quickSort(int arr[],int low,int high){
        if(low<high){
            int partitionedPivotIndex = partition(arr,low,high);

            quickSort(arr,low,partitionedPivotIndex-1);
            quickSort(arr,partitionedPivotIndex+1,high);
        }
    }

    public static void main(String[] args){
        int[] arr = new int[]{8,4,2,1};

        quickSort(arr,0,arr.length-1);

        Arrays.stream(arr).forEach(System.out::println);
    }
}
