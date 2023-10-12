package org.homio.addon.hardware;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3Block;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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
    private final Scratch3Block countryInfoReporter;

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
                blockReporter(100, "city_geo_location", "City geo [VALUE]", this::fireGetCityGeoLocationReporter,
                        block -> block.addArgument(VALUE, "unknown city"));

        this.ipGeoLocationReporter =
                blockReporter(200, "ip_geo_location", "IP geo [VALUE]", this::fireGetIPGeoLocation,
                        block -> block.addArgument(VALUE, "127.0.0.1"));

        this.countryInfoReporter =
                blockReporter(300, "country_info", "Country info [VALUE]", this::fireGetCountryInformation,
                        block -> block.addArgument(VALUE, "127.0.0.1"));

        blockHat(300, "setting_hat", "Setting [SETTING] changed to [VALUE]", this::fireSettingChangeHatEvent,
                block -> {
                    block.addArgument(SETTING, this.settingsMenu);
                    block.addArgument(VALUE);
                });

        blockHat(400, "event", "Hardware event [EVENT]", this::fireHardwareHatEvent,
                block -> block.addArgument(EVENT, this.hardwareEventsMenu));

        entityContext.event().runOnceOnInternetUp("scratch3-hardware", () -> {
            String ipAddress = networkHardwareRepository.getOuterIpAddress();
            this.ipGeoLocationReporter.addArgument(VALUE, ipAddress);
            JsonNode ipGeo = networkHardwareRepository.getIpGeoLocation(ipAddress);
            this.cityGeoLocationReporter.addArgument(
                    VALUE, ipGeo.path("city").asText());
            this.countryInfoReporter.addArgument(VALUE, ipAddress);
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
            Lock lock = workspaceBlock.getLockManager().getLock(workspaceBlock, eventName);
            workspaceBlock.subscribeToLock(lock, next::handle);
        });
    }

    private void fireSettingChangeHatEvent(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String settingName = workspaceBlock.getMenuValue(SETTING, this.settingsMenu);
            String value = workspaceBlock.getInputString(VALUE);

            Lock lock = workspaceBlock.getLockManager().getLock(workspaceBlock, settingName);
            workspaceBlock.subscribeToLock(lock, lockValue -> isEmpty(value) || value.equals(lockValue), next::handle);
        });
    }

    private State fireGetByIP(WorkspaceBlock workspaceBlock) {
        return new StringType(this.networkHardwareRepository.getOuterIpAddress());
    }

    private State fireGetIPGeoLocation(WorkspaceBlock workspaceBlock) {
        return new JsonType(
                this.networkHardwareRepository.getIpGeoLocation(
                        workspaceBlock.getInputString(VALUE)));
    }

    private State fireGetCityGeoLocationReporter(WorkspaceBlock workspaceBlock) {
        return new JsonType(
                this.networkHardwareRepository.getCityGeolocation(
                        workspaceBlock.getInputString(VALUE)));
    }

    private State fireGetCountryInformation(@NotNull WorkspaceBlock workspaceBlock) {
        return new JsonType(
                this.networkHardwareRepository.getCountryInformation(
                        workspaceBlock.getInputString(VALUE)));
    }
}
