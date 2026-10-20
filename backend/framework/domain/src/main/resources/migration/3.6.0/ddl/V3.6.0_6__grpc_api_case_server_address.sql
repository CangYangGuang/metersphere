ALTER TABLE api_grpc_case_config
    ADD COLUMN server_address VARCHAR(512) NULL COMMENT 'gRPC server address' AFTER method_name;
