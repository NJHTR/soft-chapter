-- Disposable RTC acceptance identities for a non-production database.
-- Password for all three accounts: password
-- Run explicitly with a database administrator; this file is not mounted
-- automatically by the multi-VM stacks.
USE douyin;

INSERT INTO t_user (uid, unique_id, nickname, email, password, is_delete, role)
VALUES
  (991001, 'rtc_deploy_a', 'RTC Deploy A', 'rtc-deploy-a@example.test',
   '$2a$10$0m1KU4SsNr8WOhqNmwROh..3m7wd.om4qmYLdtRNABRpAGqcpNUQm', 0, 'user'),
  (991002, 'rtc_deploy_b', 'RTC Deploy B', 'rtc-deploy-b@example.test',
   '$2a$10$0m1KU4SsNr8WOhqNmwROh..3m7wd.om4qmYLdtRNABRpAGqcpNUQm', 0, 'user'),
  (991003, 'rtc_deploy_c', 'RTC Deploy C', 'rtc-deploy-c@example.test',
   '$2a$10$0m1KU4SsNr8WOhqNmwROh..3m7wd.om4qmYLdtRNABRpAGqcpNUQm', 0, 'user')
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  email = VALUES(email),
  password = VALUES(password),
  is_delete = 0,
  role = 'user';

INSERT IGNORE INTO t_follow (user_id, follow_id)
VALUES
  (991001, 991002),
  (991002, 991001),
  (991001, 991003),
  (991003, 991001);
