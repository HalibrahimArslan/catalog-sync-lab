package com.rabbitlab.storefront;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class StorefrontApplication {

    public static void main(String[] args) {
        // Ayar dosyası: storefront.yml (application.yml değil; bkz. dosyanın başındaki not).
        new SpringApplicationBuilder(StorefrontApplication.class)
                .properties("spring.config.name=storefront")
                .run(args);
    }
}
