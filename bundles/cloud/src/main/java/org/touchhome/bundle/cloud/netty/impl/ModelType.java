package org.touchhome.bundle.cloud.netty.impl;

import lombok.Getter;

public enum ModelType {
    PingRequest(SocketPingRequestModel.class),
    PingResponse(SocketPingResponseModel.class),
    RestRequest(SocketRestRequestModel.class),
    RestResponse(SocketRestResponseModel.class);

    @Getter
    private Class<? extends SocketBaseModel> targetClass;

    ModelType(Class<? extends SocketBaseModel> targetClass) {
        this.targetClass = targetClass;
    }

    public static ModelType getType(Object o) {
        for (ModelType modelType : ModelType.values()) {
            if (modelType.targetClass.equals(o.getClass())) {
                return modelType;
            }
        }
        throw new RuntimeException("Unable to find correct ModelType for class: " + o.getClass());
    }
}
