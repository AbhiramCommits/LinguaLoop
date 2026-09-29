package com.lingualoop.api.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI linguaLoopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LinguaLoop API")
                        .description("Language-learning platform: content, study sessions, "
                                + "spaced-repetition review queue and learner stats.")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT obtained from POST /api/auth/login")));
    }
}
