package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.shared.DomainEvent;
import java.util.Collection;

/**
 * 把領域事件寫進 outbox 表。必須在呼叫端的 transaction 內執行，
 * 讓業務資料與事件一起 commit 或一起 rollback。
 */
public interface DomainEventOutbox {

    void append(Collection<? extends DomainEvent> events);
}
