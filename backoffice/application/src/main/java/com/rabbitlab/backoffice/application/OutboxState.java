package com.rabbitlab.backoffice.application;

/** Outbox'taki bir mesajın durumu (mesaj takibi). Tabloda ayrı bir sütun değil; satırdan türetilir. */
public enum OutboxState {
    /** Yazıldı, henüz denenmedi: relay'in bir sonraki turunu bekliyor. */
    PENDING,
    /** En az bir kez gönderilemedi ({@code lastError} sebebi söyler); relay denemeye devam eder. */
    FAILING,
    /** Broker onayladı ve bir kuyruğa ulaştı. */
    PUBLISHED
}
