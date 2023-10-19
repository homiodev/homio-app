package org.homio.addon.imou.internal.cloud.dto;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ResultResponse<T> {

    private String id;
    private Response<T> result;

    @Getter
    @Setter
    @ToString
    public static class Response<T> {

        private String msg;
        private String code;
        private T data;
    }
}
