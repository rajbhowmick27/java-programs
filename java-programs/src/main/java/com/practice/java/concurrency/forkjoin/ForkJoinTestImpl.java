package com.practice.java.concurrency.forkjoin;

import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;


public class ForkJoinTestImpl extends RecursiveAction {

    public static AtomicInteger count = new AtomicInteger(0);
    @Override
    protected void compute() {
        count.incrementAndGet();
    }
}
