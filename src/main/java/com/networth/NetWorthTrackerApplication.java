package com.networth;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@OpenAPIDefinition(
        info = @Info(
                title = "Indian Investment & Net-Worth Tracker API",
                version = "1.0.0",
                description = "Unified personal balance sheet for Indian investors — equity, MF, EPF, gold, crypto, real estate, loans, tax engine, and net-worth analytics.",
                contact = @Contact(name = "NetWorth Tracker")
        ),
        security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
        name = "BearerAuth",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class NetWorthTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetWorthTrackerApplication.class, args);
    }
}
