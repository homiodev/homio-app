package org.touchhome.bundle.machine;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.BroadcastLockManagerImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.BroadcastLock;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Log4j2
@Getter
@Component
public class Scratch3HardwareBlocks extends Scratch3ExtensionBlocks {

    public static final String SETTING = "SETTING";
    public static final String VALUE = "VALUE";

    private final MenuBlock.ServerMenuBlock settingsMenu;
    private final MenuBlock.ServerMenuBlock hardwareEventsMenu;

    private final Scratch3Block cityGeoLocation;
    private final Scratch3Block ipGeoLocation;
    private final Scratch3Block myIp;
    private final Scratch3Block serverTime;
    private final Scratch3Block settingChangeCommand;
    private final BroadcastLockManagerImpl broadcastLockManager;
    private final WirelessHardwareRepository wirelessHardwareRepository;
    private final Scratch3Block hardwareEventCommand;

    public Scratch3HardwareBlocks(EntityContext entityContext, BroadcastLockManagerImpl broadcastLockManager, WirelessHardwareRepository wirelessHardwareRepository) {
        super("#51633C", entityContext, null, "hardware");
        this.broadcastLockManager = broadcastLockManager;
        this.wirelessHardwareRepository = wirelessHardwareRepository;

        // Menu
        this.settingsMenu = MenuBlock.ofServer("settingsMenu", "rest/setting/name");
        this.hardwareEventsMenu = MenuBlock.ofServer("hardwareEventsMenu", "rest/hardware/event");

        // Blocks
        this.myIp = Scratch3Block.ofEvaluate(50, "my_ip", BlockType.reporter, "my ip", this::getByIP);
        this.serverTime = Scratch3Block.ofEvaluate(60, "server_time", BlockType.reporter, "server time | format [FORMAT]", this::getServerTime);
        this.serverTime.addArgument("FORMAT");

        this.cityGeoLocation = Scratch3Block.ofEvaluate(100, "city_geo_location", BlockType.reporter, "City geo [CITY] | json", this::getCityGeoLocation);
        this.cityGeoLocation.addArgument("CITY", wirelessHardwareRepository.getIpGeoLocation(wirelessHardwareRepository.getOuterIpAddress()).getCity());

        this.ipGeoLocation = Scratch3Block.ofEvaluate(200, "ip_geo_location", BlockType.reporter, "IP geo [IP] | json", this::getIPGeoLocation);
        this.ipGeoLocation.addArgument("IP", getByIP(null));

        this.settingChangeCommand = Scratch3Block.ofHandler(300, "setting_change", BlockType.hat, "Setting [SETTING] changed to [VALUE]", this::settingChangeEvent);
        this.settingChangeCommand.addArgument(SETTING, this.settingsMenu);
        this.settingChangeCommand.addArgument(VALUE);

        this.hardwareEventCommand = Scratch3Block.ofHandler(400, "hardware_event", BlockType.hat, "Hardware event [EVENT]", this::hardwareEvent);
        this.hardwareEventCommand.addArgument(EVENT, this.hardwareEventsMenu);

        this.postConstruct();
    }

    private Object getServerTime(WorkspaceBlock workspaceBlock) {
        String format = workspaceBlock.getInputString("FORMAT");
        return isEmpty(format) ? System.currentTimeMillis() : new SimpleDateFormat(format).format(new Date());
    }

    private void hardwareEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.getNextOrThrow();
        String eventName = workspaceBlock.getMenuValue(EVENT, this.hardwareEventsMenu);
        BroadcastLock<String> lock = broadcastLockManager.getOrCreateLock(workspaceBlock, eventName);
        workspaceBlock.subscribeToLock(lock);
    }

    private void settingChangeEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.getNextOrThrow();
        String settingName = workspaceBlock.getMenuValue(SETTING, this.settingsMenu);
        String value = workspaceBlock.getInputString(VALUE);

        BroadcastLock<String> lock = broadcastLockManager.getOrCreateLock(workspaceBlock, settingName);
        workspaceBlock.subscribeToLock(lock, lockValue -> isEmpty(value) || value.equals(lockValue));
    }

    private String getByIP(WorkspaceBlock workspaceBlock) {
        return this.wirelessHardwareRepository.getOuterIpAddress();
    }

    private JSONObject getIPGeoLocation(WorkspaceBlock workspaceBlock) {
        return new JSONObject(this.wirelessHardwareRepository.getIpGeoLocation(workspaceBlock.getInputString("IP")));
    }

    private JSONObject getCityGeoLocation(WorkspaceBlock workspaceBlock) {
        return new JSONObject(this.wirelessHardwareRepository.findCityGeolocation(workspaceBlock.getInputString("CITY")));
    }
}
