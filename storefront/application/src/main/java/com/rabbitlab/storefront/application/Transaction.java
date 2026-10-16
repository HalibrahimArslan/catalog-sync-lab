package com.rabbitlab.storefront.application;

import java.util.function.Supplier;

/**
 * Transaction sınırı. Backoffice'teki aynı isimli port ile aynı şekil ama ayrı arayüz: iki context
 * ortak kod paylaşmıyor (paylaştıkları tek şey mesaj sözleşmesi).
 */
public interface Transaction {

    <T> T execute(Supplier<T> work);

    default void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
