package com.practice.javaprograms.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;

public class LargestRectangleHistogram {
    public int[] maxSlidingWindow(int[] nums, int k) {
        int n = nums.length;
        int[] res = new int[n-k+1];
        int index = 0;
        Deque<Integer> dq = new ArrayDeque<>();

        for(int i=0;i<n;i++){
            // remove all out of bound indexes
            if(!dq.isEmpty() && dq.peek() == i-k)
                dq.poll();
            // remove all elements smaller than equal ti nums[i]
            while(!dq.isEmpty() && nums[dq.peekLast()] <= nums[i]){
                dq.pollLast();
            }

            dq.offer(i);

            if(i >= k-1)
                res[index++] = nums[dq.peek()];
        }


        return res;
    }

    public static void main(String[] args) {

        int[] heights = {2,3,4,5,6};
        int n = heights.length;
        Stack<Integer> st = new Stack<>();
        int maxArea = 0;
        for(int i=0;i<=n;i++){
            while(!st.isEmpty() && (i==n || heights[st.peek()] > heights[i])){
                int height = heights[st.peek()];
                st.pop();

                int width = st.isEmpty() ? i : i - st.peek() - 1;
                maxArea = Math.max(maxArea,height * width);
            }

            st.push(i);
        }

        System.out.println(maxArea);
    }
}
