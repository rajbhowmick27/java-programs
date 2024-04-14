package com.practice.javaprograms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@RestController
@EnableWebMvc
public class TestController {

    @GetMapping("/profile")
    public String getProfile(){
        return "profile";
    }
}
