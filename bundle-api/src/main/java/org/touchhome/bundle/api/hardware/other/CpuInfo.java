package org.touchhome.bundle.api.hardware.other;

import lombok.Getter;
import lombok.ToString;
import org.touchhome.bundle.api.hardware.api.ListParse;

@Getter
@ToString
public class CpuInfo {

    @ListParse.LineParse("Architecture:.*")
    private String cpuArch;
}
