package io.metersphere.api.grpc.util;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 proto 源码中提取目标 message 定义片段，供 gRPC AI Prompt 使用。
 */
public final class GrpcProtoMessageSchemaExtractor {

    private static final Pattern MESSAGE_START_PATTERN = Pattern.compile(
            "message\\s+(%s)\\s*\\{", Pattern.MULTILINE);

    private GrpcProtoMessageSchemaExtractor() {
    }

    public static String extractMessageDefinition(String protoContent, String fullTypeName) {
        if (StringUtils.isBlank(protoContent) || StringUtils.isBlank(fullTypeName)) {
            return StringUtils.EMPTY;
        }
        String simpleName = resolveSimpleName(fullTypeName);
        Pattern pattern = Pattern.compile(
                String.format(MESSAGE_START_PATTERN.pattern(), Pattern.quote(simpleName)),
                Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(protoContent);
        if (!matcher.find()) {
            return StringUtils.EMPTY;
        }
        int start = matcher.start();
        int braceStart = protoContent.indexOf('{', matcher.end() - 1);
        if (braceStart < 0) {
            return StringUtils.EMPTY;
        }
        int braceEnd = findMatchingBraceEnd(protoContent, braceStart);
        if (braceEnd < 0) {
            return StringUtils.EMPTY;
        }
        return protoContent.substring(start, braceEnd + 1).trim();
    }

    private static String resolveSimpleName(String fullTypeName) {
        int dotIndex = fullTypeName.lastIndexOf('.');
        return dotIndex >= 0 ? fullTypeName.substring(dotIndex + 1) : fullTypeName;
    }

    private static int findMatchingBraceEnd(String content, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
