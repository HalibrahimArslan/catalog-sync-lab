package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI dokümanı ({@code /v3/api-docs}): Senaryo Konsolu'nun API sekmesi uç noktaları buradan okur ve
 * oradan denenir. Doküman herkese açık; yönetim uç noktaları ({@code /admin/**}) HTTP Basic istediğini
 * dokümanda da söyler, vitrin söylemez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiDocsIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void should_describe_every_endpoint_without_credentials() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson()
                .hasPath("$.paths['/catalog'].get")
                .hasPath("$.paths['/catalog/{sku}'].get")
                .hasPath("$.paths['/admin/messages'].get")
                .hasPath("$.paths['/admin/messages/{messageId}'].get")
                .hasPath("$.paths['/admin/messages/stats'].get")
                .hasPath("$.paths['/admin/parking'].get")
                .hasPath("$.paths['/admin/parking/{queue}'].get")
                .hasPath("$.paths['/admin/parking/{queue}/replay'].post")
                .hasPath("$.paths['/admin/parking/{queue}/{messageId}/replay'].post");
    }

    @Test
    void should_mark_only_admin_endpoints_as_basic_auth() {
        var json = assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson();

        json.extractingPath("$.components.securitySchemes.admin.scheme").isEqualTo("basic");
        json.hasPath("$.paths['/admin/messages'].get.security[0].admin");
        json.hasPath("$.paths['/admin/parking/{queue}/replay'].post.security[0].admin");
        json.doesNotHavePath("$.paths['/catalog'].get.security");
    }
}
