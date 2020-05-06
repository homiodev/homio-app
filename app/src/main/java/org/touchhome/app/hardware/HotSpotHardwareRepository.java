package org.touchhome.app.hardware;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface HotSpotHardwareRepository {
    @HardwareQuery(echo = "Disable Hostapd", value = "sudo systemctl disable hostapd", ignoreOnError = true)
    @HardwareQuery(echo = "Disable Dnsmasq", value = "sudo systemctl disable dnsmasq", ignoreOnError = true)
    @HardwareQuery(echo = "Copy reuired files", printOutput = true, value =
            "sudo cp :sysDir/hostapd.conf /etc/hostapd/hostapd.conf &&" +
                    "sudo cp :sysDir/dnsmasq.conf /etc/dnsmasq.conf &&" +
                    "sudo cp :sysDir/autohotspot.service /etc/systemd/system/autohotspot.service &&" +
                    "sudo cp :sysDir/autohotspot /usr/bin/autohotspot")
    @HardwareQuery(echo = "Enable AutoHotSpot Service", value = "sudo systemctl enable autohotspot.service", printOutput = true)
    @HardwareQuery(echo = "Set AutoHotSpot executable", value = "sudo chmod +x /usr/bin/autohotspot", printOutput = true)
    void installAutoHotSpot(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("test -f /usr/bin/autohotspot")
    boolean isAutoHotSpotServiceExists();

    @HardwareQuery("sudo iw dev wlan0 set power_save off")
    void fixLooseWifi();
}









