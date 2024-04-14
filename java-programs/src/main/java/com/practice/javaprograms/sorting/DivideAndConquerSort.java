package com.practice.javaprograms.sorting;

import java.util.Arrays;

public class DivideAndConquerSort {

    private static void merge(int arr[],int l,int m,int r){
        int n1 = m-l+1;
        int n2 = r-m;

        int L[] = new int[n1];
        int R[] = new int[n2];

        for(int i=0;i<n1;i++)
            L[i]=arr[l+i];
        for(int j=0;j<n2;j++)
            R[j]=arr[m+1+j];

        int i=0, j=0, k=l;

        while(i<n1 && j<n2){
            if(L[i] <= R[j]){
                arr[k]=L[i];
                i++;
            } else {
                arr[k] = R[j];
                j++;
            }
            k++;
        }

        while(i<n1){
            arr[k] = L[i];
            k++;
            i++;
        }

        while(j<n2){
            arr[k] = R[j];
            k++;
            j++;
        }
    }

    private static void mergeSort(int arr[],int l,int r){
        if(l<r){
            int m = l + (r-l)/2;

            mergeSort(arr,l,m);
            mergeSort(arr,m+1,r);
            merge(arr,l,m,r);
        }
    }

    private static int mergeInvCount(int[] arr,int l,int m,int r){
        int[] left = Arrays.copyOfRange(arr,l,m+1);
        int[] right = Arrays.copyOfRange(arr,m+1,r+1);

        int i=0, j=0, k=l, inv_count = 0;

        while(i<left.length && j<right.length){
            if(left[i] <= right[j]){
                arr[k++] = left[i++];
            } else {
                arr[k++] = right[j++];
                inv_count += (m+1)-(l+i);
            }
        }

        while(i<left.length){
            arr[k++] = left[i++];
        }

        while(j<right.length){
            arr[k++] = right[j++];
        }

        return inv_count;
    }

    private static int invCount(int[] arr,int l,int r){
        int count = 0;

        if(l<r){
            int m = l + (r-l)/2;
            count += invCount(arr,l,m);
            count += invCount(arr,m+1,r);

            count += mergeInvCount(arr,l,m,r);
        }

        return count;
    }

    public static void main(String[] args){
        int[] arr = new int[]{12,11,13,5,6,5};
        mergeSort(arr,0,arr.length-1);

        Arrays.stream(arr).forEach(System.out::println);
        int[] arr2 = new int[]{8,4,2,1};
        System.out.println("Inv Count for arr 2 -> "+invCount(arr2,0,arr2.length-1));
    }
}
