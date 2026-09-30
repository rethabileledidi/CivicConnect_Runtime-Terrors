package com.civicconnect.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;

/** One shared Jackson mapper: ISO-8601 dates, unknown JSON fields ignored, records supported. */
public final class Json {

    /** Largest request body the API accepts (protects against oversized payloads). */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Json() { }

    public static <T> T read(InputStream in, Class<T> type) throws IOException {
        byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) throw new IOException("Request body too large");
        if (body.length == 0) throw new IOException("Request body is empty");
        return MAPPER.readValue(body, type);
    }

    public static String write(Object value) throws IOException {
        return MAPPER.writeValueAsString(value);
    }
}
