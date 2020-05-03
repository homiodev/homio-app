package org.touchhome.bundle.api.hardware.wifi;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQueries;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface HotSpotHardwareRepository {
    @HardwareQuery("sudo $PM -y install hostapd")
    void installHostapd();

    @HardwareQuery("sudo $PM -y install dnsmasq")
    void installDnsmasq();

    @HardwareQuery("sudo systemctl disable hostapd")
    void disableHostapd();

    @HardwareQuery("sudo systemctl disable dnsmasq")
    void disableDnsmasq();

    @HardwareQuery("sudo cp :sysDir/hostapd.conf /etc/hostapd/hostapd.conf")
    void copyHostapdConf(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("sudo sed -i 's/#DAEMON_CONF=\"\"/DAEMON_CONF=\"/etc/hostapd/hostapd.conf\"/g' /etc/default/hostapd\n")
    void replaceDaemonConfPath();

    @HardwareQuery("cat :sysDir/dnsmasq.conf >> /etc/dnsmasq.conf")
    void appendDnsmasqContent(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("sudo cp :sysDir/interfaces /etc/network/interfaces")
    void copyInterfaces(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("sudo echo \"nohook wpa_supplicant\" >> /etc/dhcpcd.conf")
    void ignoreWpaConfiguration();

    @HardwareQuery("sudo cp :sysDir/autohotspot.service /etc/systemd/system/autohotspot.service")
    void copyAutoHotSpotService(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("sudo systemctl enable autohotspot.service")
    void enableAutoHotSpotService();

    @HardwareQueries(value = {
            @HardwareQuery("sudo cp :sysDir/autohotspot /usr/bin/autohotspot"),
            @HardwareQuery("sudo chmod +x /usr/bin/autohotspot")
    })
    void copyAutoHotSpot(@ApiParam("sysDir") String sysDir);

    @HardwareQuery("test -f /usr/bin/autohotspot && echo true || echo false")
    boolean isAutoHotSpotServiceExists();

    @HardwareQuery(value = "sudo autohotspot swipe", printOutput = true)
    void switchHotSpot();

    @HardwareQuery("sudo iw dev wlan0 set power_save off")
    void fixLooseWifi();
}









