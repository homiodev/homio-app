package org.touchhome.bundle.api.hardware.wifi;

import lombok.Getter;
import lombok.ToString;
import org.touchhome.bundle.api.hquery.api.ListParse;

@Getter
@ToString
public class Network {

    @ListParse.BooleanLineParse(value = "IE: WPA Version 1", when = "IE: WPA Version 1", group = 0)
    private boolean encryption_wpa;

    @ListParse.BooleanLineParse(value = "IE: IEEE 802.11i/WPA2 Version 1", when = "IE: IEEE 802.11i/WPA2 Version 1", group = 0)
    private boolean encryption_wpa2;

    @ListParse.BooleanLineParse(value = "Encryption key:(on|off)", when = "on")
    private boolean encryption_any;

    @ListParse.LineParse(".* Signal level=(-??\\d+)[^\\d].*")
    private Integer strength;

    @ListParse.LineParse("Quality=(\\d+)[^\\d].*")
    private Integer quality;

    @ListParse.LineParse("Mode:(.*)")
    private String mode;

    @ListParse.LineParse("Channel:([0-9]{1,2})")
    private Integer channel;

    @ListParse.LineParse("ESSID:\"(.*)\"")
    private String ssid;

    @ListParse.LineParse(".* Address: (([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})")
    private String address;
}
