package io.metersphere.api.grpc.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

public final class GrpcJsonConverter {

    private GrpcJsonConverter() {
    }

    public static DynamicMessage requestJsonToDynamicMessage(String requestJson, Descriptors.Descriptor inputDescriptor)
            throws InvalidProtocolBufferException {
        DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(inputDescriptor);
        JsonFormat.parser().ignoringUnknownFields().merge(requestJson, requestBuilder);
        return requestBuilder.build();
    }

    public static String dynamicMessageToResponseJson(DynamicMessage response) throws InvalidProtocolBufferException {
        return dynamicMessageToResponseJson(response, true);
    }

    public static String dynamicMessageToResponseJson(DynamicMessage response, boolean includeDefaultValues)
            throws InvalidProtocolBufferException {
        JsonFormat.Printer printer = JsonFormat.printer();
        if (includeDefaultValues) {
            printer = printer.includingDefaultValueFields();
        }
        return printer.print(response);
    }
}
