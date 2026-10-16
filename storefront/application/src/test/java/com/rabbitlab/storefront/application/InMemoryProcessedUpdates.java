package com.rabbitlab.storefront.application;

import java.util.HashSet;
import java.util.Set;

class InMemoryProcessedUpdates implements ProcessedUpdates {

    final Set<String> ids = new HashSet<>();

    @Override
    public boolean markProcessed(String updateId) {
        return ids.add(updateId);
    }
}
