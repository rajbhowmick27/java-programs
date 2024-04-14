package com.practice.javaprograms.testPrograms;

import java.util.*;

public class Test {
    public static void main(String[] args){
        List<Integer> temp = new ArrayList<>();
        temp.add(1);
        temp.add(2);
        temp.add(3);

        Spliterator<Integer> si = temp.spliterator();

        List<Integer> temp2 = new ArrayList<>();
        while(si.tryAdvance(num -> temp2.add(num * 2)));

        temp2.sort(new ReverseComparator());

        Spliterator<Integer> si2 = temp2.spliterator();

        si2.forEachRemaining(System.out::println);

        Collections.sort(temp,Collections.reverseOrder());

        temp.forEach(System.out::println);

        LinkedList<Integer> temp3 = new LinkedList<>(temp);
        Iterator<Integer> reverseIterator = temp3.descendingIterator();
        while(reverseIterator.hasNext()){
            System.out.println(reverseIterator.next());
        }
    }

    public static class ReverseComparator implements Comparator<Integer> {

        @Override
        public int compare(Integer o1, Integer o2) {
//            if(o1<o2) return -1;
//            else if(o1>o2) return 1;
//            else return 0;

//            return o1-o2; // ascending
            return o2-o1; // descending
        }
    }
}
