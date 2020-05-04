package org.touchhome.bundle.api.hardware.wifi;

import org.touchhome.bundle.api.hardware.api.ListParse;

public class NetworkStat {

    @ListParse.LineParse("Mode:([^\\s]+).*")
    public String mode;

    @ListParse.LineParse(".*Frequency:(.*) GHz.*")
    public String frequency;

    @ListParse.LineParse("Bit Rate=(\\d+) Mb/s.*")
    public String bitRate;

    @ListParse.LineParse(".* ESSID:\"(.*)\"")
    public String ssid;

    @ListParse.LineParse(".* Access Point: ([a-fA-F0-9:]*)")
    public String accessPoint;

    @ListParse.LineParse(".* Signal level=(-??\\d+)[^\\d].*")
    public Integer strength;

    @ListParse.LineParse(".* Quality=(\\d+)[^\\d].*")
    public Integer quality;

    @Override
    public String toString() {
        return "NetworkStat{" +
                "mode='" + mode + '\'' +
                ", frequency='" + frequency + '\'' +
                ", bitRate='" + bitRate + '\'' +
                ", ssid='" + ssid + '\'' +
                ", accessPoint='" + accessPoint + '\'' +
                ", strength=" + strength +
                ", quality=" + quality +
                '}';
    }
}
