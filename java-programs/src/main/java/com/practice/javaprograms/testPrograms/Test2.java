package com.practice.javaprograms.testPrograms;

import com.practice.javaprograms.misc.PredicateImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Test2 {
    public static void main(String[] args){
        PredicateImpl predicate = new PredicateImpl(4);

        List<String> arr = Arrays.asList("javascript","java","nodejs","cpp");

        List<String> res = arr.stream().filter(predicate).collect(Collectors.toList());

//        System.out.println(res);

        int bal = 0;
        int ans = 0;
        String s = "())";
        for (int i = 0; i < s.length(); ++i) {

            bal += s.charAt(i) == '(' ? 1 : -1;

            // It is guaranteed bal >= -1
            if (bal == -1) {
                ans += 1;
                bal += 1;
            }
        }

        System.out.println(bal + ans);

    }
}
