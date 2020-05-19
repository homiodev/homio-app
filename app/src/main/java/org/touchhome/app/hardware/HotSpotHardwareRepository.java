package org.touchhome.app.hardware;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface HotSpotHardwareRepository {
    @HardwareQuery(echo = "Disable Hostapd", value = "systemctl disable hostapd", ignoreOnError = true)
    @HardwareQuery(echo = "Disable Dnsmasq", value = "systemctl disable dnsmasq", ignoreOnError = true)
    @HardwareQuery(echo = "Copy required files", printOutput = true, value = "mkdir -p /etc/hostapd" +
            "cp -n :sysDir/hostapd.conf /etc/hostapd/hostapd.conf &&" +
            "cp -n :sysDir/dnsmasq.conf /etc/dnsmasq.conf &&" +
            "cp -n :sysDir/autohotspot.service /etc/systemd/system/autohotspot.service &&" +
            "cp -n :sysDir/autohotspot /usr/bin/autohotspot")
    @HardwareQuery(echo = "Enable AutoHotSpot Service", value = "systemctl enable autohotspot.service", printOutput = true)
    @HardwareQuery(echo = "Set AutoHotSpot executable", value = "chmod +x /usr/bin/autohotspot", printOutput = true)
    void installAutoHotSpot(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("test -f /usr/bin/autohotspot")
    boolean isAutoHotSpotServiceExists();

    @HardwareQuery("iw dev wlan0 set power_save off")
    void fixLooseWifi();
}









