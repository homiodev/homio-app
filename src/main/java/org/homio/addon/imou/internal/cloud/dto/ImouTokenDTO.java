package org.homio.addon.imou.internal.cloud.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImouTokenDTO {
    private int expireTime;
    private String currentDomain;
    private String accessToken;
}
