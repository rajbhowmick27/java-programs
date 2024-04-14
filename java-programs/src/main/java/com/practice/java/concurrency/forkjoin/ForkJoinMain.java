package com.practice.java.concurrency.forkjoin;

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

public class ForkJoinMain {

    public static void main(String[] args) {

        ForkJoinPool pool = null;

        try{
            pool = new ForkJoinPool();
            ForkJoinTestImpl task1 = null;
            ForkJoinTestImpl task2 = null;

            for(int i=0;i<1000;i++){
                if(i%2 == 0) {
                    task1 = new ForkJoinTestImpl();
                    pool.execute(task1);
                }
                else{
                    task2 = new ForkJoinTestImpl();
                    pool.execute(task2);
                }
            }

            System.out.println("Count :: "+ForkJoinTestImpl.count);
        } catch (Exception e){
            System.out.println(e.getMessage());
        } finally {
            if(Objects.nonNull(pool))
                pool.shutdown();
        }

    }
}
