-- Base gRPC tables only (matches V3.6.0_3 + V3.6.0_4). Columns/indexes handled by Flyway on fresh DB.
SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS api_grpc_proto
(
    `id`                  VARCHAR(50)   NOT NULL COMMENT 'proto pk',
    `project_id`          VARCHAR(50)   NOT NULL COMMENT 'project fk',
    `name`                VARCHAR(255)  NOT NULL COMMENT 'proto name',
    `file_name`           VARCHAR(255)           COMMENT 'proto file name',
    `content`             LONGTEXT      NOT NULL COMMENT 'proto content',
    `descriptor_content`  LONGTEXT      NOT NULL COMMENT 'descriptor base64',
    `content_hash`        VARCHAR(64)   NOT NULL COMMENT 'content sha256',
    `create_user`         VARCHAR(50)   NOT NULL COMMENT 'create user',
    `create_time`         BIGINT        NOT NULL COMMENT 'create time',
    `update_user`         VARCHAR(50)   NOT NULL COMMENT 'update user',
    `update_time`         BIGINT        NOT NULL COMMENT 'update time',
    `deleted`             BIT(1)        NOT NULL DEFAULT 0 COMMENT 'deleted',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'gRPC proto snapshot';

CREATE TABLE IF NOT EXISTS api_grpc_definition
(
    `id`                  VARCHAR(50)   NOT NULL COMMENT 'grpc definition pk',
    `api_definition_id`   VARCHAR(50)   NOT NULL COMMENT 'api definition fk',
    `proto_id`            VARCHAR(50)   NOT NULL COMMENT 'proto fk',
    `package_name`        VARCHAR(255)           COMMENT 'proto package',
    `service_name`        VARCHAR(255)  NOT NULL COMMENT 'service name',
    `method_name`         VARCHAR(255)  NOT NULL COMMENT 'method name',
    `full_method_name`    VARCHAR(500)  NOT NULL COMMENT 'full method name',
    `server_address`      VARCHAR(512)           COMMENT 'gRPC server address',
    `method_type`         VARCHAR(50)   NOT NULL COMMENT 'method type',
    `request_type`        VARCHAR(255)           COMMENT 'request type',
    `response_type`       VARCHAR(255)           COMMENT 'response type',
    `descriptor_content`  LONGTEXT      NOT NULL COMMENT 'descriptor base64 snapshot',
    `create_user`         VARCHAR(50)   NOT NULL COMMENT 'create user',
    `create_time`         BIGINT        NOT NULL COMMENT 'create time',
    `update_user`         VARCHAR(50)   NOT NULL COMMENT 'update user',
    `update_time`         BIGINT        NOT NULL COMMENT 'update time',
    `deleted`             BIT(1)        NOT NULL DEFAULT 0 COMMENT 'deleted',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'gRPC api definition extension';

CREATE TABLE IF NOT EXISTS api_grpc_case_config
(
    `id`                  VARCHAR(50)   NOT NULL COMMENT 'grpc case config pk',
    `api_case_id`         VARCHAR(50)   NOT NULL COMMENT 'api case fk',
    `api_definition_id`   VARCHAR(50)   NOT NULL COMMENT 'api definition fk',
    `proto_id`            VARCHAR(50)   NOT NULL COMMENT 'proto fk',
    `service_name`        VARCHAR(255)  NOT NULL COMMENT 'service name',
    `method_name`         VARCHAR(255)  NOT NULL COMMENT 'method name',
    `descriptor_content`  LONGTEXT      NOT NULL COMMENT 'descriptor base64 snapshot',
    `request_json`        LONGTEXT               COMMENT 'request json',
    `metadata`            LONGTEXT               COMMENT 'metadata json',
    `timeout`             INT           NOT NULL DEFAULT 60000 COMMENT 'timeout ms',
    `create_user`         VARCHAR(50)   NOT NULL COMMENT 'create user',
    `create_time`         BIGINT        NOT NULL COMMENT 'create time',
    `update_user`         VARCHAR(50)   NOT NULL COMMENT 'update user',
    `update_time`         BIGINT        NOT NULL COMMENT 'update time',
    `deleted`             BIT(1)        NOT NULL DEFAULT 0 COMMENT 'deleted',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'gRPC api case config';
ALTER TABLE ai_conversation_content
    ADD COLUMN IF NOT EXISTS attachments MEDIUMTEXT NULL COMMENT '图片附件 JSON' AFTER content;
