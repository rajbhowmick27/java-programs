package com.practice.javaprograms.model;

import com.fasterxml.jackson.annotation.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Demo implements Serializable, Cloneable {

    private String content;
    private List<String> documents;
    private ContentType contentType;

    public void setContentType(){
        if(content != null && !content.isBlank()){
            this.contentType = Arrays.stream(ContentType.values())
                    .filter(value -> value.contentName.equalsIgnoreCase(content))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Getter
    public enum ContentType {
        BROKERAGE("Brokerage",3),
        MANAGED("Managed",2);


        private Integer priority;
        private String contentName;

        ContentType(String name,int priority){
            this.contentName = name;
            this.priority = priority;
        }
    }
}
