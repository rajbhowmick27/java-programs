package com.practice.javaprograms.sorting;

import java.util.Arrays;

public class HeapSortRecursive {

    private static void swap(int arr[],int i,int j){
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    private static void heapify(int arr[],int n,int i){
        int largest = i;
        int left = 2*i+1;
        int right = 2*i+2;

        if(left<n && arr[left] > arr[largest])
            largest = left;
        if(right<n && arr[right] > arr[largest])
            largest = right;

        // if largest is not the root node
        if(largest != i){
            swap(arr,i,largest);

            // heapify the affected the sub node for which arr[largest] is the root node
            heapify(arr,n,largest);
        }
    }


    private static void heapSort(int arr[],int n){

        // build heap
        for(int i=n/2-1;i>=0;i--){
            heapify(arr,n,i);
        }

        /*
        remove the largest element from the heap and swap the positions
        of the largest element which is the root node (or the first element of the array)
        with the element at the last index of the array or the leaf node
         */

        for(int i=n-1;i>0;i--){
            swap(arr,0,i);

            // heapify on the reduced heap
            heapify(arr,i,0);
        }

    }

    public static void main(String[] args){
        int arr[] = new int[]{8,4,2,1};

        heapSort(arr,arr.length);

        Arrays.stream(arr).forEach(System.out::println);
    }
}
