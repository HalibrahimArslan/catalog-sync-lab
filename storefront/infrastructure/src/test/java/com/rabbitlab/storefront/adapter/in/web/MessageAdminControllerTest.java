package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.DeliveryEntry;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.DeliveryQuery;
import com.rabbitlab.storefront.application.DeliverySummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.rabbitlab.storefront.application.DeliveryOutcome.PARKED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.PROCESSED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.RETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mesaj takibinin yönetim uç noktaları: durum sorgusu ve tek mesajın deneme geçmişi. */
@WebMvcTest(MessageAdminController.class)
class MessageAdminControllerTest {

    private static final Instant T1 = Instant.parse("2026-10-16T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T10:00:05Z");
    private static final DeliverySummary PARKED_STOCK = new DeliverySummary("m-1", "StockUpdated", "SKU-1",
            "storefront.stock", 1, PARKED, 3, "UnknownProductException: bilinmeyen ürün: SKU-1", T1, T2);

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DeliveryLog log;

    @Test
    void should_search_messages_by_sku_and_state() {
        when(log.find(new DeliveryQuery("SKU-1", null, PARKED, 50))).thenReturn(List.of(PARKED_STOCK));

        assertThat(mvc.get().uri("/admin/messages?sku=SKU-1&state=PARKED")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        [{"messageId": "m-1", "type": "StockUpdated", "sku": "SKU-1", "queue": "storefront.stock",
                          "schemaVersion": 1, "state": "PARKED", "attempts": 3,
                          "lastError": "UnknownProductException: bilinmeyen ürün: SKU-1",
                          "firstSeen": "2026-10-16T10:00:00Z", "lastSeen": "2026-10-16T10:00:05Z"}]""");
    }

    @Test
    void should_list_recent_messages_without_filters() {
        when(log.find(new DeliveryQuery(null, null, null, 20))).thenReturn(List.of());

        assertThat(mvc.get().uri("/admin/messages?limit=20")).hasStatusOk();
        verify(log).find(new DeliveryQuery(null, null, null, 20));
    }

    @Test
    void should_show_single_message_with_its_attempts() {
        when(log.find(new DeliveryQuery(null, "m-1", null, 1))).thenReturn(List.of(PARKED_STOCK));
        when(log.history("m-1")).thenReturn(List.of(
                new DeliveryEntry(1, RETRY, "UnknownProductException: bilinmeyen ürün: SKU-1", "storefront.stock", T1),
                new DeliveryEntry(3, PARKED, "UnknownProductException: bilinmeyen ürün: SKU-1", "storefront.stock", T2)));

        assertThat(mvc.get().uri("/admin/messages/m-1")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"summary": {"messageId": "m-1", "state": "PARKED"},
                 "attempts": [{"attempt": 1, "outcome": "RETRY", "at": "2026-10-16T10:00:00Z"},
                              {"attempt": 3, "outcome": "PARKED", "at": "2026-10-16T10:00:05Z"}]}""");
    }

    @Test
    void should_answer_not_found_for_unknown_message() {
        when(log.find(new DeliveryQuery(null, "m-9", null, 1))).thenReturn(List.of());

        assertThat(mvc.get().uri("/admin/messages/m-9")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_count_messages_by_state() {
        when(log.countByState()).thenReturn(Map.of(PROCESSED, 12L, PARKED, 2L));

        assertThat(mvc.get().uri("/admin/messages/stats")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"PROCESSED\": 12, \"PARKED\": 2, \"RETRY\": 0, \"DUPLICATE\": 0, \"REPLAYED\": 0}");
    }

    @Test
    void should_reject_unknown_state_and_non_positive_limit() {
        assertThat(mvc.get().uri("/admin/messages?state=LOST")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/admin/messages?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(log, never()).find(any());
    }
}
