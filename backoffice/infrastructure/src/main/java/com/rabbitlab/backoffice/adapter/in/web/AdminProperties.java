package com.rabbitlab.backoffice.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Yönetim uç noktalarının (/admin/outbox (mesaj takibi)) tek kullanıcısı. Şifre ayardan gelir
 * ({@code ADMIN_PASSWORD}); boşsa uygulama açılmaz.
 */
@ConfigurationProperties("backoffice.admin")
public record AdminProperties(String username, String password) {

    public AdminProperties {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("backoffice.admin.username ve backoffice.admin.password boş olamaz");
        }
    }
}
