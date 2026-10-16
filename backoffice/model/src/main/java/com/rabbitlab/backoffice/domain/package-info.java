/**
 * Backoffice'in kalbi: iş kuralları burada ve sadece burada.
 *
 * <p>Hexagonal kuralı: bu paket hiçbir şeye bağımlı değildir. Spring, JDBC, RabbitMQ, JSON
 * bilmez. Dış dünya (veritabanı, mesajlaşma, web) domain'e uyum sağlar, tersi değil.
 */
package com.rabbitlab.backoffice.domain;
