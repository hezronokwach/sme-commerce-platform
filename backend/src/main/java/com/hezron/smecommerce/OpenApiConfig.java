package com.hezron.smecommerce;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API description springdoc builds from our controllers, served at /v3/api-docs (JSON) and shown
 * at /swagger-ui.html. Postman can import the JSON to get every endpoint at once.
 * <p>
 * The "bearerAuth" scheme adds an Authorize button to Swagger UI: paste an access token from login
 * once, and every admin request is sent with "Authorization: Bearer ...".
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI smeCommerceOpenApi() {
        SecurityScheme bearerToken = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        Info info = new Info()
                .title("SME Commerce API")
                .description("Admin (/api/admin, needs a staff token), storefront (/api/store, public) "
                        + "and account (/api/auth) endpoints.")
                .version("v1");

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerToken))
                // Marks every endpoint as using the token; public ones simply ignore it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

}
