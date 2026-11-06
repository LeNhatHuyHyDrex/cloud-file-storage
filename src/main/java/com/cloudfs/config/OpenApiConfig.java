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
                        .description("REST API lưu trữ file trên object storage tương thích S3. "
                                + "Đăng nhập tại /api/auth/login rồi bấm Authorize và dán token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
