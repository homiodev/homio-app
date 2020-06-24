package org.touchhome.bundle.api.hardware.wifi;

import lombok.Getter;
import lombok.ToString;
import org.touchhome.bundle.api.hquery.api.ListParse;

@Getter
@ToString
public class NetworkStat {

    @ListParse.LineParse("Mode:([^\\s]+).*")
    private String mode;

    @ListParse.LineParse(".*Frequency:(.*) GHz.*")
    private String frequency;

    @ListParse.LineParse("Bit Rate=(\\d+) Mb/s.*")
    private String bitRate;

    @ListParse.LineParse(".* ESSID:\"(.*)\"")
    private String ssid;

    @ListParse.LineParse(".* Access Point: ([a-fA-F0-9:]*)")
    private String accessPoint;

    @ListParse.LineParse(".* Signal level=(-??\\d+)[^\\d].*")
    private Integer strength;

    @ListParse.LineParse(".* Quality=(\\d+)[^\\d].*")
    private Integer quality;
}
