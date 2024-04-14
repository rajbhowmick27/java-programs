package com.practice.javaprograms.queue;

import java.util.Stack;

public class LargestRectangleHistogram {
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
