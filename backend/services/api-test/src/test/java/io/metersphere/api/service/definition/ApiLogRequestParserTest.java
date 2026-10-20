package io.metersphere.api.service.definition;

import io.metersphere.plugin.api.spi.AbstractMsTestElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ApiLogRequestParserTest {

    @Test
    void shouldIgnoreGrpcEmptyBlobForOperationLog() {
        AbstractMsTestElement request = ApiLogRequestParser.parseMsTestElementForLog(
                "GRPC", "{}".getBytes(StandardCharsets.UTF_8));

        Assertions.assertNull(request);
    }

    @Test
    void shouldIgnoreBlobWithoutPolymorphicNameForOperationLog() {
        AbstractMsTestElement request = ApiLogRequestParser.parseMsTestElementForLog(
                "HTTP", "{\"requestJson\":\"{}\"}".getBytes(StandardCharsets.UTF_8));

        Assertions.assertNull(request);
    }

    @Test
    void shouldParseHttpBlobWithPolymorphicNameForOperationLog() {
        AbstractMsTestElement request = ApiLogRequestParser.parseMsTestElementForLog(
                "HTTP", "{\"polymorphicName\":\"MsHTTPElement\",\"children\":[]}".getBytes(StandardCharsets.UTF_8));

        Assertions.assertNotNull(request);
    }
}
