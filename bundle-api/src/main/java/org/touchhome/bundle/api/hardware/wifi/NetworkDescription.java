package org.touchhome.bundle.api.hardware.wifi;

import org.touchhome.bundle.api.hardware.api.ListParse;

public class NetworkDescription {
    @ListParse.LineParse("inet ((?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)).*")
    public String inet;

    @ListParse.LineParse(".*netmask ((?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)).*")
    public String netmask;

    @ListParse.LineParse(".*broadcast ((?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)).*")
    public String broadcast;
}
