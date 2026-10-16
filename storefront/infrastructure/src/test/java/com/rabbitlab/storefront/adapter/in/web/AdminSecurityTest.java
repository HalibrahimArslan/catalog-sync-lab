package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.CatalogQueries;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.ParkedMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Yönetim uç noktaları ({@code /admin/**}: park kuyruğu, mesaj takibi) yalnızca yöneticiye açık.
 * Önceden herkes park edilen mesajların içeriğini okuyup yeniden oynatabiliyordu (Faz 4 acıtan nokta 1).
 * Vitrin ({@code /catalog}) herkese açık kalır.
 *
 * <p>HTTP Basic: tarayıcı oturumu yok, her istek kimliğini kendisi taşır; bu yüzden CSRF koruması
 * gerekmez (yeniden oynatma gibi POST'lar da Basic ile çalışmalı).
 */
@WebMvcTest(controllers = {ParkingController.class, MessageAdminController.class, CatalogController.class},
        properties = {"storefront.admin.username=ops", "storefront.admin.password=s3cret"})
@Import(AdminSecurityConfiguration.class)
class AdminSecurityTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ParkedMessages parked;

    @MockitoBean
    DeliveryLog log;

    @MockitoBean
    CatalogQueries catalog;

    @BeforeEach
    void queues() {
        when(parked.queues()).thenReturn(List.of("storefront.stock"));
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(UTF_8));
    }

    @Test
    void should_ask_for_credentials_on_admin_endpoints() {
        assertThat(mvc.get().uri("/admin/parking")).hasStatus(HttpStatus.UNAUTHORIZED)
                .headers().hasValueSatisfying(HttpHeaders.WWW_AUTHENTICATE, value -> assertThat(value).contains("Basic"));
        assertThat(mvc.get().uri("/admin/messages")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/admin/parking/storefront.stock/replay")).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(log);
        verifyNoInteractions(catalog);
    }

    @Test
    void should_reject_wrong_password() {
        assertThat(mvc.get().uri("/admin/parking").header(HttpHeaders.AUTHORIZATION, basic("ops", "yanlış")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_let_admin_read_and_replay() {
        when(parked.replayAll("storefront.stock")).thenReturn(2);

        assertThat(mvc.get().uri("/admin/parking").header(HttpHeaders.AUTHORIZATION, basic("ops", "s3cret")))
                .hasStatusOk();
        assertThat(mvc.post().uri("/admin/parking/storefront.stock/replay")
                .header(HttpHeaders.AUTHORIZATION, basic("ops", "s3cret")))
                .as("POST de Basic ile çalışır; CSRF token istenmez").hasStatusOk();
    }

    @Test
    void should_keep_storefront_public() {
        when(catalog.visible()).thenReturn(List.of());

        assertThat(mvc.get().uri("/catalog")).hasStatusOk();
    }

    @Test
    void should_refuse_blank_credentials() { // şifresiz yönetici: uygulama hiç açılmasın
        assertThatThrownBy(() -> new AdminProperties("ops", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminProperties("", "s3cret")).isInstanceOf(IllegalArgumentException.class);
    }
}
