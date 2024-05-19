package com.practice.javaprograms.testPrograms;


import java.io.Serializable;
import java.util.*;

class SuffixPair implements Serializable {
    char suffixChar;
    int suffixWeight;

    public SuffixPair(char suffixChar,int suffixWeight){
        this.suffixChar = suffixChar;
        this.suffixWeight = suffixWeight;
    }

}


public class ReverseCalc {

    private static final List<Integer> possible_values = List.of(2,3,4,5);

    private static final Map<String,Double> weightMap = new HashMap<>();

    static{
        weightMap.put("s",0.15);
        weightMap.put("e",0.15);
        weightMap.put("a",0.1);
        weightMap.put("r",0.05);
        weightMap.put("c",0.05);
        weightMap.put("h",0.05);
    }

    public static void find(String prefix,List<Character> suffix,double arr, int n,
                            List<SuffixPair> res,
                            Map<String,Set<List<SuffixPair>>> prefixToResultSetMap,
                            Set<Character> existingChars, String baseString
                            ){
        if(arr < 0.0){
            return;
        }
        if((existingChars.size() == n) && arr < 0.50){
            Set<List<SuffixPair>> temp = prefixToResultSetMap.getOrDefault(prefix,new HashSet<>());
            temp.add(res);
            prefixToResultSetMap.put(prefix,temp);
            return;
        }

        for(int k=0;k<n;k++){
            if(!existingChars.contains(baseString.charAt(k))){
                Character newlyAddedChar = baseString.charAt(k);
                existingChars.add(baseString.charAt(k));
                for(int i=0;i<possible_values.size();i++){
                    double next_arr = arr - weightMap.get(String.valueOf(baseString.charAt(k))) * possible_values.get(i);
                    res.add(new SuffixPair(newlyAddedChar,possible_values.get(i)));
                    find(prefix,suffix,next_arr,n,res,prefixToResultSetMap,existingChars,baseString);
                    res.remove(res.size()-1);

                }
                existingChars.remove(newlyAddedChar);
            }
        }

    }

    private static void generateAllPermutations(int index, char[] chars, List<String> ans) {
        if (index == chars.length) {
            // copy the ds to ans
            StringBuilder res = new StringBuilder();
            for (int i = 0; i < chars.length; i++) {
                res.append(chars[i]);
            }
            ans.add(res.toString());
            return;
        }
        for (int i = index; i < chars.length; i++) {
            swap(i, index, chars);
            generateAllPermutations(index + 1, chars, ans);
            swap(i, index, chars);
        }
    }
    private static void swap(int i, int j, char[] arr) {
        char temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    private static List<String> generateAllPrefixes(String s){
        List<String> res = new ArrayList<>();
        for(int i=1;i<=s.length();i++){
            res.add(s.substring(0,i));
        }
        return res;
    }

    public static void main(String[] args) {

        String s = "search";
        Set<Character> baseStringSet = new HashSet<>();
        for(char c : s.toCharArray()){
            baseStringSet.add(c);
        }

        List<String> res = new ArrayList<>();
        generateAllPermutations(0,s.toCharArray(),res);

        Set<String> prefixes = new HashSet<>();
        for(String word : res){
            prefixes.addAll(generateAllPrefixes(word));
        }

        Map<String,Set<List<SuffixPair>>> prefixToResultSetMap = new HashMap<>();
        for(String prefix : prefixes){

                Set<Character> existingChars = new HashSet<>();
                for(char c : prefix.toCharArray()){
                    existingChars.add(c);
                }

                find(prefix,new ArrayList<>(),
                        1.20,s.length(),new ArrayList<>(),
                        prefixToResultSetMap,existingChars,s);

        }

        Map<Integer,String> hashToPrefixMap = new HashMap<>();
        for(String word : prefixes){
            hashToPrefixMap.put(word.hashCode(),word);
        }

        prefixToResultSetMap.keySet().forEach(key -> System.out.println(key + " -> " + prefixToResultSetMap.get(key).size()));

    }
}
