//package com.assessment.config;
//
//import com.openai.OpenAIClient;
//import com.openai.OpenAIClientBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class OpenAIConfig {
//
//    @Bean
//    public OpenAIClient openAIClient() {
//
//        return OpenAIClient.builder()
//                .apiKey(System.getenv("OPENAI_API_KEY"))
//                .build();
//    }
//}