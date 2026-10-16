package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.application.OutboxEntry;
import com.rabbitlab.backoffice.application.OutboxQueries;
import com.rabbitlab.backoffice.application.OutboxQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.rabbitlab.backoffice.application.OutboxState.FAILING;
import static com.rabbitlab.backoffice.application.OutboxState.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mesaj takibinin Backoffice tarafı: outbox'taki mesajlar ve durumları. Yönetici olarak; kimlik doğrulama {@link AdminSecurityTest}'te. */
@Import(AdminSecurityConfiguration.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(OutboxAdminController.class)
class OutboxAdminControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    OutboxQueries queries;

    @Test
    void should_search_outbox_by_sku_and_state() {
        Instant created = Instant.parse("2026-10-16T10:00:00Z");
        when(queries.find(new OutboxQuery("SKU-1", null, FAILING, 50))).thenReturn(List.of(new OutboxEntry(
                "m-1", "SKU-1", "ProductCreated", "product.created", 2, "{\"sku\":\"SKU-1\"}", created, null, 3,
                "geri döndü: NO_ROUTE (312)", FAILING)));

        assertThat(mvc.get().uri("/admin/outbox?sku=SKU-1&state=FAILING")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        [{"messageId": "m-1", "sku": "SKU-1", "type": "ProductCreated", "routingKey": "product.created",
                          "schemaVersion": 2, "payload": "{\\"sku\\":\\"SKU-1\\"}", "createdAt": "2026-10-16T10:00:00Z",
                          "publishedAt": null, "attempts": 3, "lastError": "geri döndü: NO_ROUTE (312)",
                          "state": "FAILING"}]""");
    }

    @Test
    void should_find_single_message_by_id() {
        when(queries.find(new OutboxQuery(null, "m-1", null, 50))).thenReturn(List.of());

        assertThat(mvc.get().uri("/admin/outbox?messageId=m-1")).hasStatusOk();
        verify(queries).find(new OutboxQuery(null, "m-1", null, 50));
    }

    @Test
    void should_count_by_state_including_zeros() {
        when(queries.countByState()).thenReturn(Map.of(PUBLISHED, 40L));

        assertThat(mvc.get().uri("/admin/outbox/stats")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"PENDING\": 0, \"FAILING\": 0, \"PUBLISHED\": 40}");
    }

    @Test
    void should_reject_unknown_state_and_non_positive_limit() {
        assertThat(mvc.get().uri("/admin/outbox?state=LOST")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/admin/outbox?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(queries, never()).find(any());
    }
}
