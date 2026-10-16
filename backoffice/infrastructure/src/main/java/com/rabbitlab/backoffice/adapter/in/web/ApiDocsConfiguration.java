package com.rabbitlab.backoffice.adapter.in.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI dokümanı ({@code /v3/api-docs}, springdoc): uç noktalar controller'lardan kendiliğinden çıkar.
 * Senaryo Konsolu'nun API sekmesi bu dokümanı okur; uç noktalar oradan denenir.
 *
 * <p>Yönetim uç noktaları ({@code /admin/**}) HTTP Basic ister ({@link AdminSecurityConfiguration});
 * doküman da bunu söylesin diye her birine {@value #ADMIN} güvenlik şeması eklenir.
 */
@Configuration
class ApiDocsConfiguration {

    static final String ADMIN = "admin";

    @Bean
    OpenAPI api() {
        return new OpenAPI()
                .info(new Info().title("Backoffice API").version("0.4.0")
                        .description("Ürün ve stoğun asıl kaydı. Komutlar outbox'a yazılır, RabbitMQ'ya oradan yayınlanır."))
                .components(new Components().addSecuritySchemes(ADMIN,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                                .description("Yönetici (ADMIN_USER / ADMIN_PASSWORD)")));
    }

    @Bean
    OpenApiCustomizer adminEndpointsNeedBasicAuth() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/admin/")) {
                item.readOperations().forEach(operation ->
                        operation.addSecurityItem(new SecurityRequirement().addList(ADMIN)));
            }
        });
    }
}
