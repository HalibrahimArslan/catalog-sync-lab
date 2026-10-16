package com.rabbitlab.backoffice.adapter.out.persistence;

import com.rabbitlab.backoffice.application.Transaction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** {@link Transaction} port'unun Spring uygulaması. Hata fırlarsa tüm iş geri alınır. */
@Component
class SpringTransaction implements Transaction {

    private final TransactionTemplate template;

    SpringTransaction(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
