package com.cloudfs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nimbusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nimbus Drive API")
                        .version("1.0")
                        .description("REST API for storing files on S3-compatible object storage. "
                                + "Log in at /api/auth/login, then click Authorize and paste the token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
