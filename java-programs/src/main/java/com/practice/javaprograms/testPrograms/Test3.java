package com.practice.javaprograms.testPrograms;

import java.util.Comparator;
import java.util.PriorityQueue;

class Pair<T,U>{
     T first;
     U second;
     public Pair(T first,U second){
         this.first = first;
         this.second = second;
     }

     @Override
     public String toString() {
         return "Pair{" +
                 "first=" + first +
                 ", second=" + second +
                 '}';
     }
 }

public class Test3 {

    public static double MinimiseMaxDistance(int []arr, int k){
        int n = arr.length;
        int[] howMany = new int[n-1];

        Comparator<Pair<Double,Integer>> comparator = (p1, p2) -> {
             if(p1.first.compareTo(p2.first) == 0){
                 return p2.second.compareTo(p1.second);
             }
             else
                 return Double.compare(p2.first,p1.first);
         };
        // by default priority queue stores maximum order
         PriorityQueue<Pair<Double,Integer>> pq = new PriorityQueue<>(comparator);
//        PriorityQueue<Pair> pq = new PriorityQueue<>((a, b) -> Double.compare(b.first, a.first));


        // initially store all the section lengths in the priority queue
        for(int i=0;i<n-1;i++){
            double initialSectionLength = arr[i+1] - arr[i];
            pq.offer(new Pair<Double,Integer>(initialSectionLength, i));
        }

        // for each of the gas station, find the section where it can be placed
        for(int gasStation = 1; gasStation <= k; gasStation++){
            Pair<Double,Integer> section = pq.poll();

            int sectionIndex = section.second;
            howMany[sectionIndex]++;

            double initialSectionLength = arr[sectionIndex+1] - arr[sectionIndex];
            double newSectionLength = initialSectionLength / (double)(howMany[sectionIndex] + 1);
            pq.offer(new Pair<Double,Integer>(newSectionLength, sectionIndex));
        }

        return pq.peek().first;

    }

    public static void main(String[] args) {
        int[] arr = new int[]{1,2,3,4,5};
        int m = 4;
        System.out.println(MinimiseMaxDistance(arr,4));
    }
}
