package org.homio.addon.imou.internal.cloud.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ImouDevicePowerInfoDTO {

    private Electricitys electricitys;

    @Getter
    @Setter
    @ToString
    public static class Electricitys {

        private String alkElec;
        private String litElec;
        private String electric;
        private String type;
    }
}
