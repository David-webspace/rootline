package dev.rootline.auth.application.domain.shared;

import java.util.ArrayList;
import java.util.List;

/** 聚合根基底：收集狀態變更產生的事件，由 application service 取出寫進 outbox。 */
public abstract class AggregateRoot {

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    protected void raise(DomainEvent event) {
        pendingEvents.add(event);
    }

    /** 取出並清空待發事件。 */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}
