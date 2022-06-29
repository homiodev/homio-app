package org.touchhome.bundle.hardware;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.BroadcastLockManagerImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Log4j2
@Getter
@Component
public class Scratch3HardwareBlocks extends Scratch3ExtensionBlocks {

    public static final String SETTING = "SETTING";

    private final MenuBlock.ServerMenuBlock settingsMenu;
    private final MenuBlock.ServerMenuBlock hardwareEventsMenu;

    private final Scratch3Block cityGeoLocationReporter;
    private final Scratch3Block ipGeoLocationReporter;
    private final Scratch3Block myIpReporter;
    private final Scratch3Block serverTimeReporter;
    private final Scratch3Block settingChangeHat;
    private final BroadcastLockManagerImpl broadcastLockManager;
    private final NetworkHardwareRepository networkHardwareRepository;
    private final Scratch3Block hardwareEventHat;

    public Scratch3HardwareBlocks(EntityContext entityContext, BroadcastLockManagerImpl broadcastLockManager,
                                  NetworkHardwareRepository networkHardwareRepository) {
        super("#51633C", entityContext, null, "hardware");
        this.broadcastLockManager = broadcastLockManager;
        this.networkHardwareRepository = networkHardwareRepository;

        // Menu
        this.settingsMenu = MenuBlock.ofServer("settingsMenu", "rest/setting/name", "Settings");
        this.hardwareEventsMenu = MenuBlock.ofServer("hardwareEventsMenu", "rest/hardware/event", "Event");

        // Blocks
        this.myIpReporter = Scratch3Block.ofReporter(50, "my_ip", "my ip", this::fireGetByIP);
        this.serverTimeReporter = Scratch3Block.ofReporter(60, "server_time", "time | format [FORMAT]", this::fireGetServerTimeReporter);
        this.serverTimeReporter.addArgument("FORMAT");

        this.cityGeoLocationReporter = Scratch3Block.ofReporter(100, "city_geo_location", "City geo [CITY] | json", this::fireGetCityGeoLocationReporter);
        this.cityGeoLocationReporter.addArgument("CITY", "unknown city");

        this.ipGeoLocationReporter = Scratch3Block.ofReporter(200, "ip_geo_location", "IP geo [IP] | json", this::fireGetIPGeoLocation);
        this.ipGeoLocationReporter.addArgument("IP", "127.0.0.1");

        this.settingChangeHat = Scratch3Block.ofHat(300, "setting_change", "Setting [SETTING] changed to [VALUE]", this::fireSettingChangeHatEvent);
        this.settingChangeHat.addArgument(SETTING, this.settingsMenu);
        this.settingChangeHat.addArgument(VALUE);

        this.hardwareEventHat = Scratch3Block.ofHat(400, "hardware_event", "Hardware event [EVENT]", this::fireHardwareHatEvent);
        this.hardwareEventHat.addArgument(EVENT, this.hardwareEventsMenu);

        entityContext.bgp().runOnceOnInternetUp("scratch3-hardware", () -> {
            this.ipGeoLocationReporter.addArgument("IP", fireGetByIP(null));
            this.cityGeoLocationReporter.addArgument("CITY", networkHardwareRepository.getIpGeoLocation(networkHardwareRepository.getOuterIpAddress()).getCity());
        });
    }

    private Object fireGetServerTimeReporter(WorkspaceBlock workspaceBlock) {
        String format = workspaceBlock.getInputString("FORMAT");
        return isEmpty(format) ? System.currentTimeMillis() : new SimpleDateFormat(format).format(new Date());
    }

    private void fireHardwareHatEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String eventName = workspaceBlock.getMenuValue(EVENT, this.hardwareEventsMenu);
            BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock, eventName);
            workspaceBlock.subscribeToLock(lock, next::handle);
        });
    }

    private void fireSettingChangeHatEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String settingName = workspaceBlock.getMenuValue(SETTING, this.settingsMenu);
            String value = workspaceBlock.getInputString(VALUE);

            BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock, settingName);
            workspaceBlock.subscribeToLock(lock, lockValue -> isEmpty(value) || value.equals(lockValue), next::handle);
        });
    }

    private String fireGetByIP(WorkspaceBlock workspaceBlock) {
        return this.networkHardwareRepository.getOuterIpAddress();
    }

    private JSONObject fireGetIPGeoLocation(WorkspaceBlock workspaceBlock) {
        return new JSONObject(this.networkHardwareRepository.getIpGeoLocation(workspaceBlock.getInputString("IP")));
    }

    private JSONObject fireGetCityGeoLocationReporter(WorkspaceBlock workspaceBlock) {
        return new JSONObject(this.networkHardwareRepository.findCityGeolocation(workspaceBlock.getInputString("CITY")));
    }
}
