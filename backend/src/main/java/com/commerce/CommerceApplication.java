package com.commerce;

import com.commerce.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// Repositories are declared as nested interfaces (e.g. OrderRepositories.OrderRepository); scan them too.
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableConfigurationProperties(AppProperties.class)
public class CommerceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CommerceApplication.class);
        // Development default: activate the 'local' profile unless one is chosen explicitly
        // (SPRING_PROFILES_ACTIVE, -Dspring.profiles.active or --spring.profiles.active win over this).
        // TODO before deploying: remove this block and set SPRING_PROFILES_ACTIVE=prod on the server.
        boolean explicit = System.getenv("SPRING_PROFILES_ACTIVE") != null || System.getProperty("spring.profiles.active") != null
                || java.util.Arrays.stream(args).anyMatch(a -> a.startsWith("--spring.profiles.active"));
        if (!explicit) app.setAdditionalProfiles("local");
        app.run(args);
    }
}
