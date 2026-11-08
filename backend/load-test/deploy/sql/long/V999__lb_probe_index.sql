CREATE INDEX idx_lb_probe_a ON notification (created_at, member_id);
CREATE INDEX idx_lb_probe_b ON notification (member_id, created_at, type);
