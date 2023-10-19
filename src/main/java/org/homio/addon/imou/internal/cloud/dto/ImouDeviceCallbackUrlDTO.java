package org.homio.addon.imou.internal.cloud.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ImouDeviceCallbackUrlDTO {

    private String status;
    private String callbackUrl;
    private String callbackFlag;
}
