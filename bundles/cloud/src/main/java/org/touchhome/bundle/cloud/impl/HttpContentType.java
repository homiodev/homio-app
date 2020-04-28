package org.touchhome.bundle.cloud.impl;

import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
public enum HttpContentType {
    Json(MediaType.APPLICATION_JSON_VALUE),
    JsonUtf8(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"),
    Plain(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");

    public static final Map<String, HttpContentType> contentTypeMap;

    static {
        contentTypeMap = Stream.of(HttpContentType.values()).collect(Collectors.toMap(v -> v.rawValue, v -> v));
    }

    public final String rawValue;
}
