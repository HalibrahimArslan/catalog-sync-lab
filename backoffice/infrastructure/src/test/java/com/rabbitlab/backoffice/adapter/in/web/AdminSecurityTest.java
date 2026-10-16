package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.OutboxQueries;
import com.rabbitlab.backoffice.application.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Base64;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Yönetim uç noktaları ({@code /admin/outbox}) yalnızca yöneticiye açık; ürün API'si eskisi gibi.
 * İş API'sinin kimlik doğrulaması ayrı bir konu (bilinçli kapsam dışı).
 */
@WebMvcTest(controllers = {OutboxAdminController.class, ProductController.class},
        properties = {"backoffice.admin.username=ops", "backoffice.admin.password=s3cret"})
@Import(AdminSecurityConfiguration.class)
class AdminSecurityTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    OutboxQueries outbox;

    @MockitoBean
    ProductService products;

    @MockitoBean
    InventoryService inventories;

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(UTF_8));
    }

    @Test
    void should_ask_for_credentials_on_admin_endpoints() {
        assertThat(mvc.get().uri("/admin/outbox")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/admin/outbox/stats").header(HttpHeaders.AUTHORIZATION, basic("ops", "yanlış")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(outbox);
    }

    @Test
    void should_let_admin_in() {
        when(outbox.countByState()).thenReturn(Map.of());

        assertThat(mvc.get().uri("/admin/outbox/stats").header(HttpHeaders.AUTHORIZATION, basic("ops", "s3cret")))
                .hasStatusOk();
    }

    @Test
    void should_keep_product_api_as_before() { // POST de açık: CSRF token istenmez
        assertThat(mvc.post().uri("/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku": "SKU-1", "name": "Kupa", "description": null, "price": 100, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void should_refuse_blank_credentials() { // şifresiz yönetici: uygulama hiç açılmasın
        assertThatThrownBy(() -> new AdminProperties("ops", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminProperties("", "s3cret")).isInstanceOf(IllegalArgumentException.class);
    }
}
