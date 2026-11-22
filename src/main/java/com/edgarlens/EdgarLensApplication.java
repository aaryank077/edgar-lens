package com.edgarlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EdgarLensApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdgarLensApplication.class, args);
    }
}
