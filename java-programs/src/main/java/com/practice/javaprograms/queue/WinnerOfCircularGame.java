package com.practice.javaprograms.queue;

import com.practice.javaprograms.model.Demo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WinnerOfCircularGame {
    public static int solve(int n, int k){
        if(n == 1) return 0;
        else {
            int prev = n-1;
            int out = (solve(n-1,k) + k) % n;
            System.out.println("solve("+prev+","+k+") -> "+out);
            return out;
        }
    }
    public static void main(String[] args){
//        Deque<Integer> q = new ArrayDeque<>();
//        int n = 7, k = 4;
//
//        System.out.println(solve(n,k)+1);
//
//        for(int i=1;i<=n;i++){
//            q.add(i);
//        }
//
//        while(q.size() != 1){
//            for(int i=1;i<=k;i++){
//                if(i==k){
//                    q.pollFirst();
//                }
//                else{
//                    Integer head = q.pollFirst();
//                    q.offerLast(head);
//                }
//            }
//        }
//
//        System.out.println(q.pollFirst());

        List<Demo> res = doSomeOperation(new ArrayList<>(List.of()));
        res.forEach(System.out::println);

        List<Demo> res2 = (List<Demo>) doSomeOperation2(new ArrayList<>(List.of()));
        res2.forEach(System.out::println);

    }

    private static <T extends Demo> List<T> doSomeOperation(List<T> list){
        T demo = (T) new Demo();
        demo.setContentType(Demo.ContentType.MANAGED);
        list.add(demo);
        return list;
    }

    private static List<? super Demo> doSomeOperation2(List<? super Demo> list){
        Demo demo = new Demo();
        demo.setContentType(Demo.ContentType.BROKERAGE);
        list.add(demo);
        return list;
    }
}
