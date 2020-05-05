package org.touchhome.app.hardware;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface HotSpotHardwareRepository {
    @HardwareQuery(echo = "Install Hostapd", value = "sudo $PM -y install hostapd")
    @HardwareQuery(echo = "Install Dnsmasq", value = "sudo $PM -y install dnsmasq")
    @HardwareQuery(echo = "Disable Hostapd", value = "sudo systemctl disable hostapd")
    @HardwareQuery(echo = "Disable Dnsmasq", value = "sudo systemctl disable dnsmasq")
    @HardwareQuery(echo = "copyHostapdConf", value = "sudo cp :sysDir/hostapd.conf /etc/hostapd/hostapd.conf")
    @HardwareQuery(echo = "replaceDaemonConfPath", value = "sudo sed -i 's/#DAEMON_CONF=\"\"/DAEMON_CONF=\"/etc/hostapd/hostapd.conf\"/g' /etc/default/hostapd\n")
    @HardwareQuery(echo = "appendDnsmasqContent", value = "cat :sysDir/dnsmasq.conf >> /etc/dnsmasq.conf")
    @HardwareQuery(echo = "copyHostapdConf", value = "sudo cp :sysDir/hostapd.conf /etc/hostapd/hostapd.conf")
    @HardwareQuery(echo = "copyInterfaces", value = "sudo cp :sysDir/interfaces /etc/network/interfaces")
    @HardwareQuery(echo = "Ignore wpa configuration", value = "sudo echo \"nohook wpa_supplicant\" >> /etc/dhcpcd.conf")
    @HardwareQuery(echo = "copyAutoHotSpotService", value = "sudo cp :sysDir/autohotspot.service /etc/systemd/system/autohotspot.service")
    @HardwareQuery(echo = "enableAutoHotSpotService", value = "sudo systemctl enable autohotspot.service")
    @HardwareQuery(echo = "Copy autohotspot to target directory", value = "sudo cp :sysDir/autohotspot /usr/bin/autohotspot")
    @HardwareQuery(echo = "Set autohotspot executable", value = "sudo chmod +x /usr/bin/autohotspot")
    void installHostapd(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("test -f /usr/bin/autohotspot && echo true || echo false")
    boolean isAutoHotSpotServiceExists();

    @HardwareQuery("sudo iw dev wlan0 set power_save off")
    void fixLooseWifi();
}









