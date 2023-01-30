package org.touchhome.bundle.hardware;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.api.state.ObjectType;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.state.StringType;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Log4j2
@Getter
@Component
public class Scratch3HardwareBlocks extends Scratch3ExtensionBlocks {

    public static final String SETTING = "SETTING";

    private final MenuBlock.ServerMenuBlock settingsMenu;
    private final MenuBlock.ServerMenuBlock hardwareEventsMenu;

    private final NetworkHardwareRepository networkHardwareRepository;

    private final Scratch3Block ipGeoLocationReporter;
    private final Scratch3Block cityGeoLocationReporter;

    public Scratch3HardwareBlocks(
        EntityContext entityContext, NetworkHardwareRepository networkHardwareRepository) {
        super("#51633C", entityContext, null, "hardware");
        this.networkHardwareRepository = networkHardwareRepository;

        // Menu
        this.settingsMenu = menuServer("settingsMenu", "rest/setting/name", "Settings");
        this.hardwareEventsMenu = menuServer("hardwareEventsMenu", "rest/hardware/event", "Event");

        // Blocks
        blockReporter(50, "my_ip", "my ip", this::fireGetByIP);
        blockReporter(60, "server_time", "time | format [FORMAT]", this::fireGetServerTimeReporter, block -> {
            block.addArgument("FORMAT");
        });

        this.cityGeoLocationReporter =
            blockReporter(100, "city_geo_location", "City geo [CITY] | json", this::fireGetCityGeoLocationReporter,
                block -> block.addArgument("CITY", "unknown city"));

        this.ipGeoLocationReporter =
            blockReporter(200, "ip_geo_location", "IP geo [IP] | json", this::fireGetIPGeoLocation,
                block -> block.addArgument("IP", "127.0.0.1"));

        blockHat(300, "setting_hat", "Setting [SETTING] changed to [VALUE]", this::fireSettingChangeHatEvent,
            block -> {
                block.addArgument(SETTING, this.settingsMenu);
                block.addArgument(VALUE);
            });

        blockHat(400, "event", "Hardware event [EVENT]", this::fireHardwareHatEvent,
            block -> block.addArgument(EVENT, this.hardwareEventsMenu));

        entityContext.event().runOnceOnInternetUp("scratch3-hardware", () -> {
            this.ipGeoLocationReporter.addArgument("IP", fireGetByIP(null).stringValue());
            this.cityGeoLocationReporter.addArgument(
                "CITY", networkHardwareRepository.getIpGeoLocation(networkHardwareRepository.getOuterIpAddress()).getCity());
        });
    }

    private State fireGetServerTimeReporter(WorkspaceBlock workspaceBlock) {
        String format = workspaceBlock.getInputString("FORMAT");
        return isEmpty(format)
            ? new DecimalType(System.currentTimeMillis())
            : new StringType(new SimpleDateFormat(format).format(new Date()));
    }

    private void fireHardwareHatEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String eventName = workspaceBlock.getMenuValue(EVENT, this.hardwareEventsMenu);
            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock, eventName);
            workspaceBlock.subscribeToLock(lock, next::handle);
        });
    }

    private void fireSettingChangeHatEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String settingName = workspaceBlock.getMenuValue(SETTING, this.settingsMenu);
            String value = workspaceBlock.getInputString(VALUE);

            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock, settingName);
            workspaceBlock.subscribeToLock(lock, lockValue -> isEmpty(value) || value.equals(lockValue), next::handle);
        });
    }

    private State fireGetByIP(WorkspaceBlock workspaceBlock) {
        return new StringType(this.networkHardwareRepository.getOuterIpAddress());
    }

    private State fireGetIPGeoLocation(WorkspaceBlock workspaceBlock) {
        return new ObjectType(
            this.networkHardwareRepository.getIpGeoLocation(
                workspaceBlock.getInputString("IP")));
    }

    private State fireGetCityGeoLocationReporter(WorkspaceBlock workspaceBlock) {
        return new ObjectType(
            this.networkHardwareRepository.findCityGeolocation(
                workspaceBlock.getInputString("CITY")));
    }
}
