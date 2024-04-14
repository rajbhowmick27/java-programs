package com.practice.javaprograms.segmenttree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SegmentTreeRangeMinimum {

    private final int[] arr;
    private final int[] st;

    public SegmentTreeRangeMinimum(){
        this.arr = new int[100001];
        this.st = new int[400004];
    }

    private void buildMin(int si,int ss,int se){
        if(ss == se){
            st[si] = arr[ss];
            return;
        }

        int mid = (ss + se)/2;
        buildMin(2*si,ss,mid);
        buildMin(2*si+1,mid+1,se);

        st[si] = Math.min(st[2*si],st[2*si+1]);
    }

    private void buildMax(int si,int ss,int se){
        if(ss == se){
            st[si] = arr[ss];
            return;
        }

        int mid = (ss + se)/2;
        buildMax(2*si,ss,mid);
        buildMax(2*si+1,mid+1,se);

        st[si] = Math.max(st[2*si],st[2*si+1]);
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

    private int queryMin(int si,int ss,int se,int qs,int qe){
        // completely outside
        if(ss > qe || se < qs)
            return Integer.MAX_VALUE;
        // completely inside
        if(ss >= qs && se <= qe)
            return st[si];

        int mid = (ss + se)/2;

        int l = queryMin(2*si,ss,mid,qs,qe);
        int r = queryMin(2*si+1,mid+1,se,qs,qe);

        return Math.min(l,r);
    }

    private int queryMax(int si,int ss,int se,int qs,int qe){
        // completely outside
        if(ss > qe || se < qs)
            return Integer.MIN_VALUE;
        // completely inside
        if(ss >= qs && se <= qe)
            return st[si];

        int mid = (ss + se)/2;

        int l = queryMax(2*si,ss,mid,qs,qe);
        int r = queryMax(2*si+1,mid+1,se,qs,qe);

        return Math.max(l,r);
    }

    private int querySum(int si,int ss,int se,int qs,int qe){
        // completely outside
        if(ss > qe || se < qs)
            return 0;
        // completely inside
        if(ss >= qs && se <= qe)
            return st[si];

        int mid = (ss + se)/2;

        int l = querySum(2*si,ss,mid,qs,qe);
        int r = querySum(2*si+1,mid+1,se,qs,qe);

        return l + r;
    }

    // before calling update, modify arr[qi] with given value
    private void update(int si,int ss,int se,int qi){
        if(ss == se){
            st[si] = arr[ss];
            return;
        }

        int mid = (ss + se)/2;
        if(qi <= mid)
            update(2*si,ss,mid,qi);
        else
            update(2*si+1,mid+1,se,qi);

        st[si] = Math.min(st[2*si],st[2*si+1]);
    }

    public List<Integer> findMinimumInRanges(int[] a, int[][] ranges){
        int n = a.length;
        for(int i=0;i<n;i++){
            arr[i+1] = a[i];
        }

        buildMin(1,1,n);
        List<Integer> ans = new ArrayList<>();
        for(int[] range : ranges){
            int min = queryMin(1,1,n,range[0]+1,range[1]+1);
            ans.add(min);
        }

        return ans;
    }

    public List<Integer> findMaximumInRanges(int[] a, int[][] ranges){
        int n = a.length;
        for(int i=0;i<n;i++){
            arr[i+1] = a[i];
        }

        buildMax(1,1,n);
        List<Integer> ans = new ArrayList<>();
        for(int[] range : ranges){
            int max = queryMax(1,1,n,range[0]+1,range[1]+1);
            ans.add(max);
        }

        return ans;
    }

    public static void main(String[] args) throws URISyntaxException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//        URI uri = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource("data/testInput.txt")).toURI();
//        BufferedReader br =
//                new BufferedReader(new FileReader(Paths.get(uri).toFile()));
        int n = Integer.parseInt(br.readLine().trim());
        int[] a = new int[n];
        String[] arrStr = br.readLine().trim().split(" ");
        for(int i=0;i<n;i++){
            a[i] = Integer.parseInt(arrStr[i]);
        }

        int m = Integer.parseInt(br.readLine().trim());
        int[][] ranges = new int[m][2];
        for(int i=0;i<m;i++) {
            String[] s = br.readLine().trim().split(" ");
            for (int j = 0; j < 2; j++) {
                ranges[i][j] = Integer.parseInt(s[j]);
            }
        }

        SegmentTreeRangeMinimum segmentTreeRangeMinimum = new SegmentTreeRangeMinimum();
        List<Integer> res = segmentTreeRangeMinimum.findMinimumInRanges(a, ranges);
        res.forEach(System.out::println);

    }
}
