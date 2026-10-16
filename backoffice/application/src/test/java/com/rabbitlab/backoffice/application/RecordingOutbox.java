package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

class RecordingOutbox implements EventOutbox {

    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void append(List<DomainEvent> events) {
        this.events.addAll(events);
    }
}
