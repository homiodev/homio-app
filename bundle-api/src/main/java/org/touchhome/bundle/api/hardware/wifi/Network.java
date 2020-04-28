package org.touchhome.bundle.api.hardware.wifi;

import org.touchhome.bundle.api.hardware.api.ListParse;

public class Network {

    @ListParse.BooleanLineParse(value = "IE: WPA Version 1", when = "IE: WPA Version 1", group = 0)
    public boolean encryption_wpa;

    @ListParse.BooleanLineParse(value = "IE: IEEE 802.11i/WPA2 Version 1", when = "IE: IEEE 802.11i/WPA2 Version 1", group = 0)
    public boolean encryption_wpa2;

    @ListParse.BooleanLineParse(value = "Encryption key:(on|off)", when = "on")
    public boolean encryption_any;

    @ListParse.LineParse(".* Signal level=(-??\\d+)[^\\d].*")
    public Integer strength;

    @ListParse.LineParse("Quality=(\\d+)[^\\d].*")
    public Integer quality;

    @ListParse.LineParse("Mode:(.*)")
    public String mode;

    @ListParse.LineParse("Channel:([0-9]{1,2})")
    public Integer channel;

    @ListParse.LineParse("ESSID:\"(.*)\"")
    public String ssid;

    @ListParse.LineParse(".* Address: (([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})")
    public String address;
}
