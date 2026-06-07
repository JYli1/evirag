-- 为邮箱验证码表补充安全限制字段。
-- V1 已创建基础表，本迁移加入发送 IP、单条记录发送次数和失败次数，支撑 Task 3 的限流与错误作废策略。
ALTER TABLE email_verification_codes
    ADD COLUMN sent_ip VARCHAR(64) NULL COMMENT '发送验证码的来源 IP' AFTER created_at,
    ADD COLUMN send_count INT NOT NULL DEFAULT 1 COMMENT '单条验证码记录发送次数，当前实现每条记录固定为 1，不表示每日累计次数' AFTER sent_ip,
    ADD COLUMN failure_count INT NOT NULL DEFAULT 0 COMMENT '验证码校验失败次数' AFTER send_count;
