package org.touchhome.bundle.api.hardware.wifi;

import io.swagger.annotations.ApiParam;
import lombok.SneakyThrows;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.api.ErrorsHandler;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;
import org.touchhome.bundle.api.hardware.api.ListParse;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

@HardwareRepositoryAnnotation
public interface WirelessHardwareRepository {
    @HardwareQuery(echo = "Switch hotspot", value = "autohotspot swipe", printOutput = true)
    void switchHotSpot();

    @HardwareQuery("iwlist :iface scan")
    @ErrorsHandler(onRetCodeError = "Got some major errors from our scan command",
            notRecognizeError = "Got some errors from our scan command",
            errorHandlers = {
                    @ErrorsHandler.ErrorHandler(onError = "Device or resource busy", throwError = "Scans are overlapping; slow down putToCache frequency"),
                    @ErrorsHandler.ErrorHandler(onError = "Allocation failed", throwError = "Too many networks for iwlist to handle")
            })
    @ListParse(delimiter = ".*Cell \\d\\d.*", clazz = Network.class)
    List<Network> scan(@ApiParam("iface") String iface);

    @HardwareQuery("iwconfig :iface")
    @ErrorsHandler(onRetCodeError = "Error getting wireless devices information", errorHandlers = {})
    NetworkStat stat(@ApiParam("iface") String iface);

    @HardwareQuery("ifconfig wlan0 down")
    @ErrorsHandler(onRetCodeError = "There was an unknown error disabling the interface", notRecognizeError = "There was an error disabling the interface", errorHandlers = {})
    void disable();

    @HardwareQuery(echo = "Restart network interface", value = "/etc/init.d/networking restart", printOutput = true)
    void restartNetworkInterface();

    @HardwareQuery("ifconfig wlan0 up")
    @ErrorsHandler(onRetCodeError = "There was an unknown error enabling the interface",
            notRecognizeError = "There was an error enabling the interface",
            errorHandlers = {
                    @ErrorsHandler.ErrorHandler(onError = "No such device", throwError = "The interface wlan0 does not exist."),
                    @ErrorsHandler.ErrorHandler(onError = "Allocation failed", throwError = "Too many networks for iwlist to handle")
            })
    void enable();

    @HardwareQuery("iwconfig wlan0 essid ':essid' key :PASSWORD")
    void connect_wep(String essid, String password);

    @ErrorsHandler(onRetCodeError = "Shit is broken TODO", errorHandlers = {})
    @HardwareQuery("wpa_passphrase ':essid' ':password' > wpa-temp.conf && sudo wpa_supplicant -D wext -i wlan0 -c wpa-temp.conf && rm wpa-temp.conf")
    void connect_wpa(String essid, String password);

    @HardwareQuery("iwconfig wlan0 essid ':essid'")
    void connect_open(String essid);

    @HardwareQuery(value = "ifconfig :iface", ignoreOnError = true)
    NetworkDescription getNetworkDescription(@ApiParam("iface") String iface);

    @HardwareQuery("grep -r 'psk=' /etc/wpa_supplicant/wpa_supplicant.conf | cut -d = -f 2 | cut -d \\\" -f 2")
    String getWifiPassword();

    @SneakyThrows
    default void setWifiCredentials(String ssid, String password, String country) {
        String value = TouchHomeUtils.templateBuilder("wpa_supplicant.conf")
                .set("SSID", ssid).set("PASSWORD", password).set("COUNTRY", country)
                .build();

        Files.write(Paths.get("/etc/wpa_supplicant/wpa_supplicant.conf"), value.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    @HardwareQuery("ip addr | awk '/state UP/ {print $2}' | sed 's/.$//'")
    String getActiveNetworkInterface();

    @HardwareQuery("iw :iface set power_save off")
    void setWifiPowerSaveOff(@ApiParam("iface") String iface);

    @HardwareQuery("test -f ~/.ssh/id_rsa")
    boolean isSshGenerated();

    @HardwareQuery("cat /dev/zero | ssh-keygen -q -N \"\"")
    void generateSSHKeys();

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
        return !EntityContext.isLinuxEnvironment() ? null :
                getNetworkDescription(getActiveNetworkInterface());
    }
}
