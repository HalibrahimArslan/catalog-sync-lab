/**
 * Use case'ler: "Backoffice ne yapabilir?" sorusunun cevabı.
 *
 * <p>Onion'ın üçüncü halkası. Dış halka (web) servisleri doğrudan çağırır; Faz 2'deki inbound port
 * arayüzleri yok (P3-M2). Bu halkanın altyapıdan istedikleri ({@link EventOutbox}, {@link Transaction})
 * burada arayüz olarak tanımlı, uygulamaları en dış halkada. Repository'ler domain servisleri halkasında.
 * Bu paket framework bilmez.
 */
package com.rabbitlab.backoffice.application;
