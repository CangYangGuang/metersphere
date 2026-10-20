-- Upgrade-only patch: columns for DBs where Flyway already passed 3.6.0.5.
SET SESSION innodb_lock_wait_timeout = 7200;
SET @db = DATABASE();
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'assertion_json');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_case_config ADD COLUMN assertion_json LONGTEXT COMMENT ''assertion json'' AFTER timeout',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_definition' AND COLUMN_NAME = 'server_address');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_definition ADD COLUMN server_address VARCHAR(512) NULL COMMENT ''gRPC server address'' AFTER full_method_name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'extract_json');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_case_config ADD COLUMN extract_json LONGTEXT COMMENT ''extract json'' AFTER assertion_json',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'server_address');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_case_config ADD COLUMN server_address VARCHAR(512) NULL COMMENT ''gRPC server address'' AFTER method_name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'settings_json');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_case_config ADD COLUMN settings_json LONGTEXT COMMENT ''grpc settings json'' AFTER extract_json',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'pre_processor_json');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_case_config ADD COLUMN pre_processor_json LONGTEXT COMMENT ''grpc pre processor json'' AFTER settings_json',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'post_processor_json');
SET @sql = IF(@exist = 0,
    'ALTER TABLE api_grpc_case_config ADD COLUMN post_processor_json LONGTEXT COMMENT ''grpc post processor json'' AFTER pre_processor_json',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE ai_conversation_content
    ADD COLUMN IF NOT EXISTS attachments MEDIUMTEXT NULL COMMENT '图片附件 JSON' AFTER content;
