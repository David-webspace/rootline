package dev.rootline.dept.application.port;

import java.util.UUID;

/** 消費端冪等（必須在處理事件的 transaction 內呼叫）。 */
public interface ProcessedEvents {

    /** @return 第一次看到這個事件時為 true */
    boolean markProcessed(UUID eventId, String consumer);
}
