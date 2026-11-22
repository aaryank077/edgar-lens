package com.edgarlens.sec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SecConfig {

    @Bean
    HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    SecClient secClient(SecProperties props, HttpClient httpClient, ObjectMapper mapper) {
        return new SecClient(props, httpClient, mapper);
    }
}
