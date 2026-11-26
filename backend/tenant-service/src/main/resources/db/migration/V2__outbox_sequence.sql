-- outbox 依寫入順序送出：同一個 transaction 內的多個事件 created_at 可能相同，
-- 用遞增序號保證順序（例如合併部門時「移出人員」一定排在「標記已合併」之前）
ALTER TABLE outbox_events ADD COLUMN seq BIGSERIAL;
DROP INDEX IF EXISTS idx_outbox_status_created;
CREATE INDEX idx_outbox_status_seq ON outbox_events (status, seq);
