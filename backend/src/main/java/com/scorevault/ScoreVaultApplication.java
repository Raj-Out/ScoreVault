package com.scorevault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ScoreVaultApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScoreVaultApplication.class, args);
    }
}
