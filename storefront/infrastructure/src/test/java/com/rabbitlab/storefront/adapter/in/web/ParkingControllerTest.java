package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.ParkedMessage;
import com.rabbitlab.storefront.application.ParkedMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * P4-M2 — Park kuyruğunun yönetim uç noktaları. Burada yönetici olarak ({@code @WithMockUser});
 * kimlik doğrulamanın kendisi {@link AdminSecurityTest}'te.
 */
@Import(AdminSecurityConfiguration.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(ParkingController.class)
class ParkingControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ParkedMessages parked;

    @BeforeEach
    void queues() {
        when(parked.queues()).thenReturn(List.of("storefront.catalog", "storefront.stock"));
    }

    @Test
    void should_list_parking_queues() {
        assertThat(mvc.get().uri("/admin/parking")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("[\"storefront.catalog\", \"storefront.stock\"]");
    }

    @Test
    void should_list_parked_messages_of_a_queue() {
        when(parked.list("storefront.stock", 50)).thenReturn(List.of(new ParkedMessage("m-1", "StockUpdated",
                "UnknownProductException: bilinmeyen ürün: SKU-1", 3, "{\"sku\":\"SKU-1\",\"quantity\":4}")));

        assertThat(mvc.get().uri("/admin/parking/storefront.stock")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"messageId": "m-1", "type": "StockUpdated", "error": "UnknownProductException: bilinmeyen ürün: SKU-1",
                  "attempts": 3, "body": "{\\"sku\\":\\"SKU-1\\",\\"quantity\\":4}"}]""");
    }

    @Test
    void should_pass_limit_to_listing() {
        when(parked.list("storefront.stock", 5)).thenReturn(List.of());

        assertThat(mvc.get().uri("/admin/parking/storefront.stock?limit=5")).hasStatusOk();
        verify(parked).list("storefront.stock", 5);
    }

    @Test
    void should_replay_single_message() {
        when(parked.replay("storefront.stock", "m-1")).thenReturn(true);

        assertThat(mvc.post().uri("/admin/parking/storefront.stock/m-1/replay")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void should_answer_not_found_when_message_is_not_parked() {
        when(parked.replay("storefront.stock", "m-9")).thenReturn(false);

        assertThat(mvc.post().uri("/admin/parking/storefront.stock/m-9/replay")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_replay_all_messages_of_a_queue() {
        when(parked.replayAll("storefront.catalog")).thenReturn(2);

        assertThat(mvc.post().uri("/admin/parking/storefront.catalog/replay")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"replayed\": 2}");
    }

    @Test
    void should_answer_not_found_for_unknown_queue() {
        assertThat(mvc.get().uri("/admin/parking/backoffice.secret")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/admin/parking/backoffice.secret/replay")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/admin/parking/backoffice.secret/m-1/replay")).hasStatus(HttpStatus.NOT_FOUND);

        verify(parked, atLeastOnce()).queues();
        verifyNoMoreInteractions(parked);
    }

    @Test
    void should_reject_non_positive_limit() {
        assertThat(mvc.get().uri("/admin/parking/storefront.stock?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(parked, never()).list(anyString(), anyInt());
    }
}
