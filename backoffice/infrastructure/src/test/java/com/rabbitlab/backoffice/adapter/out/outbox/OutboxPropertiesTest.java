package com.rabbitlab.backoffice.adapter.out.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-M3 — Yayınlanan sözleşme sürümü ayardan gelir; desteklenmeyen bir sürümle uygulama hiç açılmamalı.
 * Açılsaydı hata ancak ilk ürün kaydedilirken, kullanıcının isteği sırasında çıkardı.
 */
class OutboxPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    void should_publish_v1_by_default() { // güvenli sıra: önce tüketici iki sürümü okur, sonra üretici geçer
        context.run(ctx -> assertThat(ctx.getBean(OutboxProperties.class).schemaVersion()).isEqualTo(1));
    }

    @Test
    void should_accept_v2() {
        context.withPropertyValues("backoffice.outbox.schema-version=2")
                .run(ctx -> assertThat(ctx.getBean(OutboxProperties.class).schemaVersion()).isEqualTo(2));
    }

    @Test
    void should_refuse_to_start_with_unsupported_version() {
        context.withPropertyValues("backoffice.outbox.schema-version=3")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause().hasMessageContaining("3"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxProperties.class)
    static class PropertiesOnly {
    }
}
