package com.zhou.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhou.review.mapper")
public class IntelligentStudyReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligentStudyReviewApplication.class, args);
    }

}
