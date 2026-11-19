package com.nova.constructor;

import org.springframework.boot.SpringApplication;

/**
 * Sobe o backend com o classpath de teste (H2 em modo MySQL) para experimentar sem MySQL:
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=test}. Em uso real, o banco é sempre MySQL.
 */
public class TestNovaApplication {

    public static void main(String[] args) {
        SpringApplication.from(NovaApplication::main).run(args);
    }
}
