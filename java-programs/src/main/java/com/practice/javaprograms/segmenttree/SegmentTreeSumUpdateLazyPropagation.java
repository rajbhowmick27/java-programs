package com.practice.javaprograms.segmenttree;

public class SegmentTreeSumUpdateLazyPropagation {

    private final int[] arr;
    private final int[] st;

    private final int[] lazy;

    public SegmentTreeSumUpdateLazyPropagation(){
        this.arr = new int[100001];
        this.st = new int[400004];
        this.lazy = new int[400004];
    }
    private void buildSum(int si,int ss,int se){
        if(ss == se){
            st[si] = arr[ss];
            return;
        }

        int mid = (ss + se)/2;
        buildSum(2*si,ss,mid);
        buildSum(2*si+1,mid+1,se);

        st[si] = st[2*si] + st[2*si+1];
    }

    private int querySumWithLazy(int si,int ss,int se,int qs,int qe){
        if(lazy[si] != 0){
            int dx = lazy[si];
            lazy[si] = 0;
            st[si] += dx * (se-ss+1);

            if(ss != se){
                lazy[2*si] += dx;
                lazy[2*si+1] += dx;
            }
        }
        // completely outside
        if(ss > qe || se < qs)
            return 0;
        // completely inside
        if(ss >= qs && se <= qe)
            return st[si];

        int mid = (ss + se)/2;

        int l = querySumWithLazy(2*si,ss,mid,qs,qe);
        int r = querySumWithLazy(2*si+1,mid+1,se,qs,qe);

        return l + r;
    }

    private void updateLazy(int si,int ss,int se,int qs,int qe,int val){
        if(lazy[si] != 0){
            int dx = lazy[si];
            lazy[si] = 0;
            st[si] += dx * (se-ss+1);

            if(ss != se){
                lazy[2*si] += dx;
                lazy[2*si+1] += dx;
            }
        }
        // completely outside
        if(ss > qe || se < qs)
            return;
        // completely inside
        if(ss >= qs && se <= qe){
            int dx = (se-ss+1) * val;
            st[si] += dx;

            if(ss != se){
                lazy[2*si] += val;
                lazy[2*si+1] += val;
                return;
            }
        }

        int mid = (ss + se) / 2;

        updateLazy(2*si,ss,mid,qs,qe,val);
        updateLazy(2*si+1,mid+1,se,qs,qe,val);

        st[si] = st[2*si] + st[2*si+1];
    }

    public static void main(String[] args) {

    }
}
