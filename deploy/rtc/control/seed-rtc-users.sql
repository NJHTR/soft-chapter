-- Local-only RTC browser identities. Password for all four users: password
-- This file is mounted only by docker-compose.rtc-control.yml and is never a production seed.
USE douyin;

INSERT INTO t_user (uid, unique_id, nickname, phone, email, password, is_delete)
VALUES
  (900001, 'rtc_test_a', 'RTC Test A', '19900000001', 'rtc-a@local.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0),
  (900002, 'rtc_test_b', 'RTC Test B', '19900000002', 'rtc-b@local.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0),
  (900003, 'rtc_test_c', 'RTC Test C', '19900000003', 'rtc-c@local.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0),
  (900004, 'rtc_test_d', 'RTC Test D', '19900000004', 'rtc-d@local.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  password = VALUES(password),
  is_delete = 0;
