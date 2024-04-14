package com.practice.javaprograms.testPrograms;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.javaprograms.model.Demo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestUtil {

    public static void main(String[] args) throws URISyntaxException, IOException {
//        Demo demo = getDataFromJson("data/test.json",Demo.class);
//        demo.setContentType();
//        System.out.println(demo);

        List<Integer> bigs = Arrays.asList(1000,2000,3000);
        System.out.println(sumInteger(bigs) == sum(bigs));
        System.out.println(sumInteger(bigs) == sumInteger(bigs));
        System.out.println(sumInteger(bigs).equals(sumInteger(bigs)));

        List<Integer> smalls = Arrays.asList(1,2,3);
        System.out.println(sumInteger(smalls) == sum(smalls));
        System.out.println(sumInteger(smalls) == sumInteger(smalls));
    }

    private static <T> T getDataFromJson(String fname,Class<T> type) throws URISyntaxException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
        URI uri = ClassLoader.getSystemClassLoader().getResource(fname).toURI();
        String content = Files.readAllLines(Paths.get(uri)).stream().collect(Collectors.joining());
        return mapper.readValue(content,mapper.getTypeFactory().constructType(type));
    }

    private static Integer sumInteger(List<Integer> ints){
        Integer s = 0;
        for(Integer n : ints) s += n;
        return s;
    }

    private static int sum(List<Integer> ints){
        int s = 0;
        for(int n : ints) s += n;
        return s;
    }
}
