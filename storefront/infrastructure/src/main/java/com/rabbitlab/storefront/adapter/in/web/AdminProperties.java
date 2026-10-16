package com.rabbitlab.storefront.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Yönetim uç noktalarının (/admin/parking (park kuyruğu), /admin/messages (mesaj takibi)) tek kullanıcısı. Şifre ayardan gelir
 * ({@code ADMIN_PASSWORD}); boşsa uygulama açılmaz.
 */
@ConfigurationProperties("storefront.admin")
public record AdminProperties(String username, String password) {

    public AdminProperties {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("storefront.admin.username ve storefront.admin.password boş olamaz");
        }
    }
}
