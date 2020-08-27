package org.touchhome.bundle.hardware;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.BroadcastLockManagerImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.BroadcastLock;

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
    private final Scratch3Block settingChangeCommand;
    private final BroadcastLockManagerImpl broadcastLockManager;
    private final Scratch3Block hardwareEventCommand;

    public Scratch3HardwareBlocks(EntityContext entityContext, BroadcastLockManagerImpl broadcastLockManager) {
        super("#51633C", entityContext, null, "hardware");
        this.broadcastLockManager = broadcastLockManager;

        // Menu
        this.settingsMenu = MenuBlock.ofServer("settingsMenu", "rest/setting/name", "-", "-");
        this.hardwareEventsMenu = MenuBlock.ofServer("hardwareEventsMenu", "rest/hardware/event", "-", "-");

        // Blocks
        this.myIp = Scratch3Block.ofEvaluate(50, "my_ip", BlockType.reporter, "my ip", this::getByIP);

        this.cityGeoLocation = Scratch3Block.ofEvaluate(100, "city_geo_location", BlockType.reporter, "City geo [CITY] (json)", this::getCityGeoLocation);
        this.cityGeoLocation.addArgument("CITY", ArgumentType.string,
                TouchHomeUtils.getIpGeoLocation(TouchHomeUtils.getOuterIpAddress()).getCity());

        this.ipGeoLocation = Scratch3Block.ofEvaluate(200, "ip_geo_location", BlockType.reporter, "IP geo [IP] (json)", this::getIPGeoLocation);
        this.ipGeoLocation.addArgument("IP", ArgumentType.string, getByIP(null));

        this.settingChangeCommand = Scratch3Block.ofHandler(300, "setting_change", BlockType.hat, "Setting [SETTING] changed to [VALUE]", this::settingChangeEvent);
        this.settingChangeCommand.addArgumentServerSelection(SETTING, this.settingsMenu);
        this.settingChangeCommand.addArgument(VALUE, ArgumentType.string);

        this.hardwareEventCommand = Scratch3Block.ofHandler(400, "hardware_event", BlockType.hat, "Hardware event [EVENT]", this::hardwareEvent);
        this.hardwareEventCommand.addArgumentServerSelection(EVENT, this.hardwareEventsMenu);

        this.postConstruct();
    }

    private void hardwareEvent(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasNext()) {
            String eventName = workspaceBlock.getMenuValue(EVENT, this.hardwareEventsMenu, String.class);

            BroadcastLock<String> lock = broadcastLockManager.getOrCreateLock(workspaceBlock, eventName);

            workspaceBlock.subscribeToLock(lock);
        }
    }

    private void settingChangeEvent(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasNext()) {
            String settingName = workspaceBlock.getMenuValue(SETTING, this.settingsMenu, String.class);
            String value = workspaceBlock.getInputString(VALUE);

            BroadcastLock<String> lock = broadcastLockManager.getOrCreateLock(workspaceBlock, settingName);
            workspaceBlock.subscribeToLock(lock, lockValue -> StringUtils.isEmpty(value) || value.equals(lockValue));
        }
    }

    private String getByIP(WorkspaceBlock workspaceBlock) {
        return TouchHomeUtils.getOuterIpAddress();
    }

    private JSONObject getIPGeoLocation(WorkspaceBlock workspaceBlock) {
        return new JSONObject(TouchHomeUtils.getIpGeoLocation(workspaceBlock.getInputString("IP")));
    }

    private JSONObject getCityGeoLocation(WorkspaceBlock workspaceBlock) {
        return new JSONObject(TouchHomeUtils.findCityGeolocation(workspaceBlock.getInputString("CITY")));
    }
}
