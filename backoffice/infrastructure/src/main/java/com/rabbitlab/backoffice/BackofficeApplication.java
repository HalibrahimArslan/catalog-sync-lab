package com.rabbitlab.backoffice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class BackofficeApplication {

    public static void main(String[] args) {
        // Ayar dosyası: backoffice.yml (application.yml değil; bkz. dosyanın başındaki not).
        new SpringApplicationBuilder(BackofficeApplication.class)
                .properties("spring.config.name=backoffice")
                .run(args);
    }
}
