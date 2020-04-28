package org.touchhome.bundle.cloud.impl;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;

import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

@Getter
@NoArgsConstructor
class SocketRestResponseModel extends SocketBaseModel {

    private int requestId;
    private HttpContentType contentType;
    private byte[] response;
    private String error;
    private int status;

    private SocketRestResponseModel(int requestId) {
        this.requestId = requestId;
    }

    static SocketRestResponseModel ofError(int requestId, Exception ex) {
        SocketRestResponseModel model = new SocketRestResponseModel(requestId);
        model.error = ex.getMessage();
        return model;
    }

    static SocketRestResponseModel ofServletResponse(int requestId, HttpServletResponseAdapter response) {
        SocketRestResponseModel model = new SocketRestResponseModel(requestId);
        model.response = response.getOutputStream().getArray();
        String headerLength = response.getHeader(CONTENT_LENGTH);
        if (headerLength != null) {
            int length = Integer.parseInt(headerLength);
            model.response = Arrays.copyOfRange(model.response, 0, length);
        }
        model.contentType = HttpContentType.contentTypeMap.get(response.getHeader(CONTENT_TYPE));
        model.status = response.getStatus();
        return model;
    }
}
