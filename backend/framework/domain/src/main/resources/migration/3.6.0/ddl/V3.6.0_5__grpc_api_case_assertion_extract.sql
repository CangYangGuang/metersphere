SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE api_grpc_case_config
    ADD COLUMN assertion_json LONGTEXT COMMENT '断言配置 JSON' AFTER timeout,
    ADD COLUMN extract_json LONGTEXT COMMENT '变量提取配置 JSON' AFTER assertion_json;
