package com.abc.connect.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.transforms.Transformation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka Connect SMT for schemaless JSON values.
 *
 * It copies selected Kafka record headers into the record value Map.
 * Useful when a sink connector writes only the value to storage, for example ADLS Gen2 Sink.
 *
 * Example output:
 * {
 *   "customerId": "1001",
 *   "amount": 250,
 *   "Header": {
 *     "messageId": "12323",
 *     "timestamp": "12323"
 *   }
 * }
 */
public class HeadersToMap<R extends ConnectRecord<R>> implements Transformation<R> {

    public static final String HEADERS_CONFIG = "headers";
    public static final String TARGET_FIELD_CONFIG = "target.field";
    public static final String WRAP_PAYLOAD_CONFIG = "wrap.payload";
    public static final String PAYLOAD_FIELD_CONFIG = "payload.field";
    public static final String INCLUDE_MISSING_CONFIG = "include.missing.headers";

    private List<String> headerNames;
    private String targetField;
    private boolean wrapPayload;
    private String payloadField;
    private boolean includeMissingHeaders;

    @Override
    public void configure(Map<String, ?> configs) {
        String headers = String.valueOf(configs.getOrDefault(HEADERS_CONFIG, ""));
        this.headerNames = new ArrayList<>();
        Arrays.stream(headers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(this.headerNames::add);

        this.targetField = String.valueOf(configs.getOrDefault(TARGET_FIELD_CONFIG, "Header"));
        this.wrapPayload = Boolean.parseBoolean(String.valueOf(configs.getOrDefault(WRAP_PAYLOAD_CONFIG, "false")));
        this.payloadField = String.valueOf(configs.getOrDefault(PAYLOAD_FIELD_CONFIG, "Payload"));
        this.includeMissingHeaders = Boolean.parseBoolean(String.valueOf(configs.getOrDefault(INCLUDE_MISSING_CONFIG, "false")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public R apply(R record) {
        if (record == null || record.value() == null) {
            return record;
        }

        Object value = record.value();
        if (!(value instanceof Map)) {
            // This SMT is intentionally for schemaless JSON Map values only.
            return record;
        }

        Map<String, Object> originalValue = new LinkedHashMap<>((Map<String, Object>) value);
        Map<String, Object> headerMap = new LinkedHashMap<>();

        for (String headerName : headerNames) {
            Header header = record.headers().lastWithName(headerName);
            if (header != null) {
                headerMap.put(headerName, convertHeaderValue(header.value()));
            } else if (includeMissingHeaders) {
                headerMap.put(headerName, null);
            }
        }

        Map<String, Object> newValue;
        if (wrapPayload) {
            newValue = new LinkedHashMap<>();
            newValue.put(targetField, headerMap);
            newValue.put(payloadField, originalValue);
        } else {
            newValue = new LinkedHashMap<>(originalValue);
            newValue.put(targetField, headerMap);
        }

        return record.newRecord(
                record.topic(),
                record.kafkaPartition(),
                record.keySchema(),
                record.key(),
                Schema.OPTIONAL_MAP_SCHEMA,
                newValue,
                record.timestamp()
        );
    }

    private Object convertHeaderValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = ((ByteBuffer) value).asReadOnlyBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    @Override
    public ConfigDef config() {
        return new ConfigDef()
                .define(HEADERS_CONFIG, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE,
                        ConfigDef.Importance.HIGH, "Comma-separated Kafka header names to copy into the record value.")
                .define(TARGET_FIELD_CONFIG, ConfigDef.Type.STRING, "Header",
                        ConfigDef.Importance.MEDIUM, "Target field name where copied headers will be stored.")
                .define(WRAP_PAYLOAD_CONFIG, ConfigDef.Type.BOOLEAN, false,
                        ConfigDef.Importance.MEDIUM, "If true, output becomes {Header:{...}, Payload:{original value}}.")
                .define(PAYLOAD_FIELD_CONFIG, ConfigDef.Type.STRING, "Payload",
                        ConfigDef.Importance.MEDIUM, "Payload field name used when wrap.payload=true.")
                .define(INCLUDE_MISSING_CONFIG, ConfigDef.Type.BOOLEAN, false,
                        ConfigDef.Importance.LOW, "If true, missing headers are included with null values.");
    }

    @Override
    public void close() {
        // No resources to close.
    }
}
