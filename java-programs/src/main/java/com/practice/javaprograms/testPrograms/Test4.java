package com.practice.javaprograms.testPrograms;

import java.util.Arrays;

public class Test4 {

    private static void swapTwoElementsIfGreater(long[] a,long[] b,int ind1, int ind2){
        if(a[ind1] > b[ind2]){
            long temp = a[ind1];
            a[ind1] = b[ind2];
            b[ind2] = temp;
        }
    }

    public static void main(String[] args) {


        long[] a = new long[]{0,6,6,6,6,6,7,8};
        long[] b = new long[]{5,5,6,6,8};
        int n = a.length;
        int m = b.length;
        int len = n + m;
        int gap = (len/2) + (len%2);

        while(gap > 0){
            int left = 0;
            int right = left + gap;

            while(right < len){
                // left in a , right in b
                if(left < n && right >= n){
                    swapTwoElementsIfGreater(a, b, left, right - n);
                }
                // both left and right in b
                else if(left >= n){
                    swapTwoElementsIfGreater(b, b, left - n, right - n);
                }
                // both left and right in a
                else {
                    swapTwoElementsIfGreater(a, a, left, right);
                }
                left++;
                right++;
            }
            if(gap == 1)
                break;
            gap = (gap/2) + (gap%2);
        }

        Arrays.stream(a).forEach(System.out::println);
        System.out.println("----------");
        Arrays.stream(b).forEach(System.out::println);
    }
}
