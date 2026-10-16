package com.rabbitlab.backoffice.domain;

import com.rabbitlab.backoffice.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate'lerin ortak tabanı: olan biteni domain event'i olarak biriktirir.
 *
 * <p>Aggregate event'i kendisi yayınlamaz (RabbitMQ'yu bilmez). Application katmanı aggregate'i
 * kaydederken {@link #pullEvents()} ile event'leri alır ve outbox'a yazar (P2-M2).
 *
 * <p>Repository'nin iki sorusu var:
 * <ul>
 *   <li>{@link #isNew()}: hiç kaydedilmedi mi? (insert mü update mi)</li>
 *   <li>{@link #persistedVersion()}: veritabanından okunduğunda hangi versiyondaydı? Kaydederken
 *       "veritabanında hâlâ bu versiyon mu var?" diye sorulur (optimistic locking).</li>
 * </ul>
 * İkisi ayrı tutuluyor çünkü versiyon "yeni" anlamına gelmez: hiç sayılmamış bir stok kayıtlıdır
 * ama versiyonu 0'dır.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> events = new ArrayList<>();
    private final boolean isNew;
    private final long persistedVersion;

    /** Yeni oluşturulan, henüz kaydedilmemiş aggregate. */
    protected AggregateRoot() {
        this.isNew = true;
        this.persistedVersion = 0;
    }

    /** Veritabanından geri yüklenen aggregate. */
    protected AggregateRoot(long persistedVersion) {
        this.isNew = false;
        this.persistedVersion = persistedVersion;
    }

    public boolean isNew() {
        return isNew;
    }

    public long persistedVersion() {
        return persistedVersion;
    }

    protected void record(DomainEvent event) {
        events.add(event);
    }

    /** Biriken event'leri verir ve listeyi boşaltır; aynı event iki kez alınmaz. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
