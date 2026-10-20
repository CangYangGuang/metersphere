package io.metersphere.api.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.Data;

@Data
public class ApiGrpcDefinition implements Serializable {
    private String id;
    private String apiDefinitionId;
    private String protoId;
    private String packageName;
    private String serviceName;
    private String methodName;
    private String fullMethodName;
    private String serverAddress;
    private String methodType;
    private String requestType;
    private String responseType;
    private String descriptorContent;
    private String createUser;
    private Long createTime;
    private String updateUser;
    private Long updateTime;
    private Boolean deleted;

    private static final long serialVersionUID = 1L;

    public enum Column {
        id("id", "id", "VARCHAR", false),
        apiDefinitionId("api_definition_id", "apiDefinitionId", "VARCHAR", false),
        protoId("proto_id", "protoId", "VARCHAR", false),
        packageName("package_name", "packageName", "VARCHAR", false),
        serviceName("service_name", "serviceName", "VARCHAR", false),
        methodName("method_name", "methodName", "VARCHAR", false),
        fullMethodName("full_method_name", "fullMethodName", "VARCHAR", false),
        serverAddress("server_address", "serverAddress", "VARCHAR", false),
        methodType("method_type", "methodType", "VARCHAR", false),
        requestType("request_type", "requestType", "VARCHAR", false),
        responseType("response_type", "responseType", "VARCHAR", false),
        descriptorContent("descriptor_content", "descriptorContent", "LONGVARCHAR", false),
        createUser("create_user", "createUser", "VARCHAR", false),
        createTime("create_time", "createTime", "BIGINT", false),
        updateUser("update_user", "updateUser", "VARCHAR", false),
        updateTime("update_time", "updateTime", "BIGINT", false),
        deleted("deleted", "deleted", "BIT", false);

        private static final String BEGINNING_DELIMITER = "`";

        private static final String ENDING_DELIMITER = "`";

        private final String column;

        private final boolean isColumnNameDelimited;

        private final String javaProperty;

        private final String jdbcType;

        public String value() {
            return this.column;
        }

        public String getValue() {
            return this.column;
        }

        public String getJavaProperty() {
            return this.javaProperty;
        }

        public String getJdbcType() {
            return this.jdbcType;
        }

        Column(String column, String javaProperty, String jdbcType, boolean isColumnNameDelimited) {
            this.column = column;
            this.javaProperty = javaProperty;
            this.jdbcType = jdbcType;
            this.isColumnNameDelimited = isColumnNameDelimited;
        }

        public String desc() {
            return this.getEscapedColumnName() + " DESC";
        }

        public String asc() {
            return this.getEscapedColumnName() + " ASC";
        }

        public static Column[] excludes(Column ... excludes) {
            ArrayList<Column> columns = new ArrayList<>(Arrays.asList(Column.values()));
            if (excludes != null && excludes.length > 0) {
                columns.removeAll(new ArrayList<>(Arrays.asList(excludes)));
            }
            return columns.toArray(new Column[]{});
        }

        public static Column[] all() {
            return Column.values();
        }

        public String getEscapedColumnName() {
            if (this.isColumnNameDelimited) {
                return new StringBuilder().append(BEGINNING_DELIMITER).append(this.column).append(ENDING_DELIMITER).toString();
            } else {
                return this.column;
            }
        }

        public String getAliasedEscapedColumnName() {
            return this.getEscapedColumnName();
        }
    }
}
