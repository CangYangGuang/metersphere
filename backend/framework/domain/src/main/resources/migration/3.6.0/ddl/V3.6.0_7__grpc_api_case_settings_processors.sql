SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE api_grpc_case_config
    ADD COLUMN settings_json LONGTEXT COMMENT 'gRPC 运行设置 JSON' AFTER extract_json,
    ADD COLUMN pre_processor_json LONGTEXT COMMENT 'gRPC 前置操作配置 JSON' AFTER settings_json,
    ADD COLUMN post_processor_json LONGTEXT COMMENT 'gRPC 后置操作配置 JSON' AFTER pre_processor_json;
