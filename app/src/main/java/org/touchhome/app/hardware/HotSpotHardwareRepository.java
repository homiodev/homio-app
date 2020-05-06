package org.touchhome.app.hardware;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface HotSpotHardwareRepository {
    @HardwareQuery(echo = "Install Hostapd", value = "sudo $PM -y install hostapd && sudo systemctl disable hostapd")
    @HardwareQuery(echo = "Install Dnsmasq", value = "sudo $PM -y install dnsmasq && sudo systemctl disable dnsmasq")
    @HardwareQuery(echo = "Copy Hostapd Conf", value = "sudo cp :sysDir/hostapd.conf /etc/hostapd/hostapd.conf")
    @HardwareQuery(echo = "Replace Daemon ConfPath", value = "sudo sed -i 's/#DAEMON_CONF=\"\"/DAEMON_CONF=\"/etc/hostapd/hostapd.conf\"/g' /etc/default/hostapd\n")
    @HardwareQuery(echo = "Append Dnsmasq Content", value = "cat :sysDir/dnsmasq.conf >> /etc/dnsmasq.conf")
    @HardwareQuery(echo = "Copy Interfaces", value = "sudo cp :sysDir/interfaces /etc/network/interfaces")
    @HardwareQuery(echo = "Ignore wpa configuration", value = "sudo echo \"nohook wpa_supplicant\" >> /etc/dhcpcd.conf")
    @HardwareQuery(echo = "Copy AutoHotSpot Service", value = "sudo cp :sysDir/autohotspot.service /etc/systemd/system/autohotspot.service")
    @HardwareQuery(echo = "Enable AutoHotSpot Service", value = "sudo systemctl enable autohotspot.service")
    @HardwareQuery(echo = "Copy AutoHotSpot to target directory", value = "sudo cp :sysDir/autohotspot /usr/bin/autohotspot")
    @HardwareQuery(echo = "Set AutoHotSpot executable", value = "sudo chmod +x /usr/bin/autohotspot")
    void installAutoHotSpot(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("test -f /usr/bin/autohotspot")
    boolean isAutoHotSpotServiceExists();

    @HardwareQuery("sudo iw dev wlan0 set power_save off")
    void fixLooseWifi();
}









