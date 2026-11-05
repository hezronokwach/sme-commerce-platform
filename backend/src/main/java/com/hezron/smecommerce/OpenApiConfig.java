package com.hezron.smecommerce;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * The API description springdoc builds from our controllers, served at /v3/api-docs (JSON) and shown
 * at /swagger-ui.html. Postman can import the JSON; the frontends generate their API clients from it.
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

    @Bean
    OperationCustomizer readableOperationNames() {
        return new ReadableOperationNames();
    }

    /**
     * Names every operation after its controller and method, and groups it under the controller's name:
     * ProductController.list becomes operation "productList" in group "Product". The generated frontend
     * clients use these names, so the admin calls {@code productService.productList()}.
     * <p>
     * Without this, springdoc would use only the method name, and the four "list" methods would be called
     * list, list_1, list_2 and list_3.
     */
    static class ReadableOperationNames implements OperationCustomizer {

        private static final String CONTROLLER_SUFFIX = "Controller";

        @Override
        public Operation customize(Operation operation, HandlerMethod handlerMethod) {
            // "ProductController" -> "Product"
            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String group = controllerName;
            if (controllerName.endsWith(CONTROLLER_SUFFIX)) {
                group = controllerName.substring(0, controllerName.length() - CONTROLLER_SUFFIX.length());
            }

            // "Product" + "list" -> "productList"
            String methodName = handlerMethod.getMethod().getName();
            String firstLetterLowercase = group.substring(0, 1).toLowerCase() + group.substring(1);
            String firstLetterUppercase = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            operation.setOperationId(firstLetterLowercase + firstLetterUppercase);

            List<String> tags = new ArrayList<>();
            tags.add(group);
            operation.setTags(tags);
            return operation;
        }

    }

}
