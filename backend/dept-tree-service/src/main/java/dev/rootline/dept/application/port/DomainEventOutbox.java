package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.permission.PermissionEvent;
import java.util.Collection;

/** 要通知其他服務的事件寫進 outbox，與業務資料同一個 transaction。 */
public interface DomainEventOutbox {

    void append(Collection<? extends DepartmentEvent> events);

    void appendPermissionEvents(Collection<? extends PermissionEvent> events);
}
