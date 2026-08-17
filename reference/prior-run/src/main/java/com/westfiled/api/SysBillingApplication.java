package com.westfiled.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SysBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SysBillingApplication.class, args);
    }

}
