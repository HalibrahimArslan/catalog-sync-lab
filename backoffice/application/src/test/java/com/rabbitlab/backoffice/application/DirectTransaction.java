package com.rabbitlab.backoffice.application;


import java.util.function.Supplier;

/** Unit testte transaction yok; iş doğrudan çalışır. Rollback'i entegrasyon testi kanıtlar. */
class DirectTransaction implements Transaction {

    @Override
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
