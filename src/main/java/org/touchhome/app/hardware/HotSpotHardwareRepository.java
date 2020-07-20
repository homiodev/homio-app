package org.touchhome.app.hardware;

import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface HotSpotHardwareRepository {
    @HardwareQuery(echo = "Disable Hostapd", value = "systemctl disable hostapd", ignoreOnError = true)
    @HardwareQuery(echo = "Disable Dnsmasq", value = "systemctl disable dnsmasq", ignoreOnError = true)
    @HardwareQuery(echo = "Copy required files", printOutput = true, value = "mkdir -p /etc/hostapd && " +
            "cp -n :sysDir/hostapd.conf /etc/hostapd/hostapd.conf && " +
            "cp -n :sysDir/dnsmasq.conf /etc/dnsmasq.conf && " +
            "cp -n :sysDir/autohotspot.service /etc/systemd/system/autohotspot.service && " +
            "cp -n :sysDir/autohotspot /usr/bin/autohotspot")
    @HardwareQuery(echo = "Enable AutoHotSpot Service", value = "systemctl enable autohotspot.service", printOutput = true, ignoreOnError = true)
    @HardwareQuery(echo = "Set AutoHotSpot executable", value = "chmod +x /usr/bin/autohotspot", printOutput = true, ignoreOnError = true)
    void installAutoHotSpot(@HQueryParam("sysDir") String sysDir);

    @HardwareQuery("test -f /usr/bin/autohotspot")
    boolean isAutoHotSpotServiceExists();
}









