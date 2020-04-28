package org.touchhome.bundle.cloud.impl;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpMethod;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
class SocketRestRequestModel extends SocketBaseModel {
    private int requestId;
    private HttpMethod httpMethod;
    private String path;
    private HttpContentType contentType;
    private Map<String, String[]> parameters;
    private int contentLength;
    private byte[] request;
}
