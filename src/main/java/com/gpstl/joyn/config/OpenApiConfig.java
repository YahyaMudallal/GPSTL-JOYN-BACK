package com.gpstl.joyn.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation Swagger : http://localhost:8080/swagger-ui.html
 * Le bouton "Authorize" permet de coller un token JWT pour tester les routes protégées.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Joyn API", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
