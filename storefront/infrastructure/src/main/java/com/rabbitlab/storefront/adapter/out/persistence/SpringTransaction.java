package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.application.Transaction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

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
