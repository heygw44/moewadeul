ALTER TABLE notification_setting DROP FOREIGN KEY fk_notification_setting_member;

ALTER TABLE notification_setting
    ADD CONSTRAINT fk_notification_setting_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE;
