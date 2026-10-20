SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS api_grpc_case_config
(
    `id`                  VARCHAR(50)   NOT NULL COMMENT 'gRPC 用例配置 pk',
    `api_case_id`         VARCHAR(50)   NOT NULL COMMENT '接口用例 fk',
    `api_definition_id`   VARCHAR(50)   NOT NULL COMMENT '接口定义 fk',
    `proto_id`            VARCHAR(50)   NOT NULL COMMENT 'proto fk',
    `service_name`        VARCHAR(255)  NOT NULL COMMENT 'Service 名称',
    `method_name`         VARCHAR(255)  NOT NULL COMMENT 'Method 名称',
    `descriptor_content`  LONGTEXT      NOT NULL COMMENT 'descriptor Base64 快照',
    `request_json`        LONGTEXT               COMMENT '请求 JSON',
    `metadata`            LONGTEXT               COMMENT 'Metadata JSON',
    `timeout`             INT           NOT NULL DEFAULT 60000 COMMENT '超时 ms',
    `create_user`         VARCHAR(50)   NOT NULL COMMENT '创建人',
    `create_time`         BIGINT        NOT NULL COMMENT '创建时间',
    `update_user`         VARCHAR(50)   NOT NULL COMMENT '修改人',
    `update_time`         BIGINT        NOT NULL COMMENT '修改时间',
    `deleted`             BIT(1)        NOT NULL DEFAULT 0 COMMENT '删除状态',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'gRPC 接口用例配置';

CREATE UNIQUE INDEX uk_api_case_id ON api_grpc_case_config (api_case_id);
CREATE INDEX idx_api_definition_id ON api_grpc_case_config (api_definition_id);
CREATE INDEX idx_proto_id ON api_grpc_case_config (proto_id);
