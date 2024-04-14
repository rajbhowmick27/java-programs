package com.practice.javaprograms.sorting;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

public class KthSmallest {

    private static int findKthSmallestWithPriorityQueue(int[] arr,int k){

        PriorityQueue<Integer> pq = new PriorityQueue<>(k, (a,b) -> b - a);
        Set<Integer> st = new HashSet<>();
        for(int i=0;i<arr.length;i++){
            if(pq.size() < k && !st.contains(arr[i])){
                pq.offer(arr[i]);
                st.add(arr[i]);
            }
            else {
                if(pq.peek() > arr[i]){
                    st.remove(pq.poll());
                    pq.offer(arr[i]);
                    st.add(arr[i]);
                }
            }
        }

        return !pq.isEmpty() ? pq.peek() : -1;
    }

    private static int findKthSmallestUsingCountingSort(int[] arr,int k){
        int max = 0;
        for(int i=0;i<arr.length;i++){
            max = Math.max(max,arr[i]);
        }

        int[] freq = new int[max+1];

        for(int i=0;i<arr.length;i++){
            if(freq[arr[i]] == 0)
                freq[arr[i]]++;
        }


        int smallest = 0;
        for(int num=0;num<=max;num++){
            // if element is present
            if(freq[num] > 0){
                smallest += freq[num];
            }

            if(smallest >= k){
                return num;
            }
        }

        return -1;
    }

    private static int findKthLargestWithPriorityQueue(int[] arr,int k){

        PriorityQueue<Integer> pq = new PriorityQueue<>(k, (a,b) -> a - b);
        Set<Integer> st = new HashSet<>();
        for(int i=0;i<arr.length;i++){
            if(pq.size() < k && !st.contains(arr[i])){
                pq.offer(arr[i]);
                st.add(arr[i]);
            }
            else {
                if(pq.peek() < arr[i]){
                    st.remove(pq.poll());
                    pq.offer(arr[i]);
                    st.add(arr[i]);
                }
            }
        }

        return !pq.isEmpty() ? pq.peek() : -1;
    }

    public static void main(String[] args){
      int[] arr = new int[]{4,3,8,2};
      int k = 2;

      int[] arr1 = new int[]{0,0,0,2,3,3,3};

      System.out.println(findKthSmallestWithPriorityQueue(arr1,k));
      System.out.println(findKthSmallestUsingCountingSort(arr1,k));
      System.out.println(findKthLargestWithPriorityQueue(arr1,k));

    }
}
