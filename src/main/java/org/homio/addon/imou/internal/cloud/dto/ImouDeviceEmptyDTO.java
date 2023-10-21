package org.homio.addon.imou.internal.cloud.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ImouDeviceEmptyDTO {

    private List<ImouDeviceDTO> devices;
    private int count;

}
