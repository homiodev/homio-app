package org.touchhome.bundle.api.hardware.wifi;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.ErrorsHandler;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;
import org.touchhome.bundle.api.hardware.api.ListParse;

import java.net.URL;
import java.net.URLConnection;
import java.util.List;

@HardwareRepositoryAnnotation
public interface WirelessHardwareRepository {
    @HardwareQuery(value = "sudo autohotspot swipe", printOutput = true)
    void switchHotSpot();

    @HardwareQuery("sudo iwlist wlan0 scan")
    @ErrorsHandler(onRetCodeError = "Got some major errors from our scan command",
            notRecognizeError = "Got some errors from our scan command",
            errorHandlers = {
                    @ErrorsHandler.ErrorHandler(onError = "Device or resource busy", throwError = "Scans are overlapping; slow down putToCache frequency"),
                    @ErrorsHandler.ErrorHandler(onError = "Allocation failed", throwError = "Too many networks for iwlist to handle")
            })
    @ListParse(delimiter = ".*Cell \\d\\d.*", clazz = Network.class)
    List<Network> scan();

    @HardwareQuery("sudo iwconfig wlan0")
    @ErrorsHandler(onRetCodeError = "Error getting wireless devices information", errorHandlers = {})
    NetworkStat stat();

    @HardwareQuery("sudo ifconfig wlan0 down")
    @ErrorsHandler(onRetCodeError = "There was an unknown error disabling the interface", notRecognizeError = "There was an error disabling the interface", errorHandlers = {})
    void disable();

    @HardwareQuery(value = "sudo /etc/init.d/networking restart", printOutput = true)
    void restartNetworkInterface();

    @HardwareQuery("sudo ifconfig wlan0 up")
    @ErrorsHandler(onRetCodeError = "There was an unknown error enabling the interface",
            notRecognizeError = "There was an error enabling the interface",
            errorHandlers = {
                    @ErrorsHandler.ErrorHandler(onError = "No such device", throwError = "The interface wlan0 does not exist."),
                    @ErrorsHandler.ErrorHandler(onError = "Allocation failed", throwError = "Too many networks for iwlist to handle")
            })
    void enable();

    @HardwareQuery("sudo iwconfig wlan0 essid ':essid' key :PASSWORD")
    void connect_wep(String essid, String password);

    @ErrorsHandler(onRetCodeError = "Shit is broken TODO", errorHandlers = {})
    @HardwareQuery("sudo wpa_passphrase ':essid' ':password' > wpa-temp.conf && sudo wpa_supplicant -D wext -i wlan0 -c wpa-temp.conf && rm wpa-temp.conf")
    void connect_wpa(String essid, String password);

    @HardwareQuery("sudo iwconfig wlan0 essid ':essid'")
    void connect_open(String essid);

    @HardwareQuery(value = "sudo ifconfig :iface", ignoreOnError = true)
    NetworkDescription getNetworkDescription(@ApiParam("iface") String iface);

    @HardwareQuery("sudo grep -r 'psk=' /etc/wpa_supplicant/wpa_supplicant.conf | cut -d = -f 2 | cut -d \\\" -f 2")
    String getWifiPassword();

    @HardwareQuery("wpa_passphrase \":ssid\" \":password\" > /etc/wpa_supplicant/wpa_supplicant.conf")
    void setWifiPassword(@ApiParam("ssid") String ssid, @ApiParam("password") String password);

    @HardwareQuery("ip addr | awk '/state UP/ {print $2}' | sed 's/.$//'")
    String getActiveNetworkInterface();

    default boolean hasInternetAccess(String spec) {
        try {
            URL url = new URL(spec);
            URLConnection connection = url.openConnection();
            connection.connect();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    default NetworkDescription getNetworkDescription() {
        String activeNetworkInterface = getActiveNetworkInterface();
        return getNetworkDescription(activeNetworkInterface);
    }
}
