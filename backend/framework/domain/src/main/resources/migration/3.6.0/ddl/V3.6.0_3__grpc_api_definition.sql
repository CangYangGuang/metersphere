SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS api_grpc_proto
(
    `id`                  VARCHAR(50)   NOT NULL COMMENT 'proto pk',
    `project_id`          VARCHAR(50)   NOT NULL COMMENT '项目fk',
    `name`                VARCHAR(255)  NOT NULL COMMENT 'proto 名称',
    `file_name`           VARCHAR(255)           COMMENT 'proto 文件名',
    `content`             LONGTEXT      NOT NULL COMMENT 'proto 原文',
    `descriptor_content`  LONGTEXT      NOT NULL COMMENT 'descriptor Base64',
    `content_hash`        VARCHAR(64)   NOT NULL COMMENT 'content SHA-256',
    `create_user`         VARCHAR(50)   NOT NULL COMMENT '创建人',
    `create_time`         BIGINT        NOT NULL COMMENT '创建时间',
    `update_user`         VARCHAR(50)   NOT NULL COMMENT '修改人',
    `update_time`         BIGINT        NOT NULL COMMENT '修改时间',
    `deleted`             BIT(1)        NOT NULL DEFAULT 0 COMMENT '删除状态',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'gRPC proto 快照';

CREATE INDEX idx_project_id ON api_grpc_proto (project_id);
CREATE INDEX idx_content_hash ON api_grpc_proto (project_id, content_hash);

CREATE TABLE IF NOT EXISTS api_grpc_definition
(
    `id`                  VARCHAR(50)   NOT NULL COMMENT 'gRPC 定义 pk',
    `api_definition_id`   VARCHAR(50)   NOT NULL COMMENT '接口定义 fk',
    `proto_id`            VARCHAR(50)   NOT NULL COMMENT 'proto fk',
    `package_name`        VARCHAR(255)           COMMENT 'proto package',
    `service_name`        VARCHAR(255)  NOT NULL COMMENT 'Service 名称',
    `method_name`         VARCHAR(255)  NOT NULL COMMENT 'Method 名称',
    `full_method_name`    VARCHAR(500)  NOT NULL COMMENT '完整方法名',
    `server_address`      VARCHAR(512)           COMMENT 'gRPC 服务地址',
    `method_type`         VARCHAR(50)   NOT NULL COMMENT 'UNARY/STREAMING 类型',
    `request_type`        VARCHAR(255)           COMMENT '请求 Message 类型',
    `response_type`       VARCHAR(255)           COMMENT '响应 Message 类型',
    `descriptor_content`  LONGTEXT      NOT NULL COMMENT 'descriptor Base64 快照',
    `create_user`         VARCHAR(50)   NOT NULL COMMENT '创建人',
    `create_time`         BIGINT        NOT NULL COMMENT '创建时间',
    `update_user`         VARCHAR(50)   NOT NULL COMMENT '修改人',
    `update_time`         BIGINT        NOT NULL COMMENT '修改时间',
    `deleted`             BIT(1)        NOT NULL DEFAULT 0 COMMENT '删除状态',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'gRPC 接口定义扩展';

CREATE INDEX idx_api_definition_id ON api_grpc_definition (api_definition_id);
CREATE INDEX idx_proto_id ON api_grpc_definition (proto_id);
