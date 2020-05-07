package org.touchhome.bundle.api.hardware;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.hardware.wifi.Network;
import org.touchhome.bundle.api.hardware.wifi.NetworkStat;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;

@Log4j2
@Component
@RequiredArgsConstructor
public class WirelessManager {

    private final WirelessHardwareRepository wirelessHardwareRepository;
    private Timer hotspotCancelTimer;

    public NetworkStat connectToWPANetwork(String apSSID, String password) {
        return connectToNetworkInternal(apSSID, network -> {
            if (network.isEncryption_wpa2()) {
                wirelessHardwareRepository.connect_wpa(apSSID, password);
                return true;
            } else {
                log.error("Unable connect to network ssid: <{}>. Network has no wpa2 encryption", apSSID);
            }
            return false;
        });
    }

    public NetworkStat connectToOpenNetwork(String apSSID, String iface) {
        return connectToNetworkInternal(apSSID, network -> {
            if (!network.isEncryption_any()) {
                wirelessHardwareRepository.connect_open(apSSID);
                return true;
            } else {
                log.error("Unable connect to network ssid: <{}>. Network has encryption", apSSID);
            }
            return false;
        });
    }

    private NetworkStat connectToNetworkInternal(String apSSID, Function<Network, Boolean> connector) {
        NetworkStat networkStat = wirelessHardwareRepository.stat();
        if (networkStat.getSsid().equals(apSSID)) {
            log.info("Already connected to ssid: <{}>", apSSID);
            return networkStat;
        }
        // connect only if we are not connected yet
        Optional<Network> optionalNetwork = wirelessHardwareRepository.scan().stream().filter(n -> n.getSsid().startsWith(apSSID)).findAny();
        if (optionalNetwork.isPresent()) {
            Network network = optionalNetwork.get();
            // connect only if AP is open
            if (connector.apply(network)) {
                wirelessHardwareRepository.connect_open(apSSID);

                networkStat = wirelessHardwareRepository.stat();
                if (networkStat.getSsid().equals(apSSID)) {
                    return networkStat;

                } else {
                    log.error("Unable change wifi accessPoint after trying connect to <{}>", apSSID);
                }
            }
        } else {
            log.error("Unable find network with ssid <{}>", apSSID);
        }
        return null;
    }

    public void enableHotspot(int hotSpotEnableTimeout) {
        if (hotspotCancelTimer == null) {
            this.hotspotCancelTimer = new Timer("hotspot-cancel-timer");

            log.info("Enabling hotspot for <{}> seconds", hotSpotEnableTimeout);
            this.wirelessHardwareRepository.switchHotSpot();
            this.hotspotCancelTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    log.info("Disabling hotspot");
                    wirelessHardwareRepository.switchHotSpot();
                    hotspotCancelTimer = null;
                }
            }, hotSpotEnableTimeout * 1000);
        }
    }
}
