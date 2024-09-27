package org.homio.app.chromecast;

import jakarta.persistence.Entity;
import lombok.RequiredArgsConstructor;
import org.homio.api.Context;
import org.homio.api.entity.HasPlace;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Entity
@UISidebarChildren(icon = "fab fa-chromecast", color = "#3C69A3", allowCreateItem = false)
public class ChromecastEntity extends MediaEntity implements
        DeviceEndpointsBehaviourContract,
        EntityService<ChromecastService>,
        HasPlace,
        HasFirmwareVersion {

    @Override
    public @NotNull Class<ChromecastService> getEntityServiceItemClass() {
        return ChromecastService.class;
    }

    @Override
    public @Nullable ChromecastService createService(@NotNull Context context) {
        return new ChromecastService(context, this);
    }

    @UIField(order = 1, disableEdit = true)
    public @Nullable String getHost() {
        return getJsonData("host");
    }

    @UIField(order = 2, disableEdit = true)
    public int getPort() {
        return getJsonData("port", 22);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "chromecast";
    }

    @Override
    public @Nullable String getDefaultName() {
        return getChromecastType().name();
    }

    @UIField(order = 10, label = "chromecastRefreshRate")
    @UIFieldSlider(min = 5, max = 60, header = "sec.")
    public int getRefreshRate() {
        return getJsonData("rate", 10);
    }

    @UIField(order = 20, disableEdit = true)
    public @NotNull ChromecastType getChromecastType() {
        return getJsonDataEnum("type", ChromecastType.Chromecast);
    }

    public void setRefreshRate(int value) {
        setJsonData("rate", value);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @Override
    public @NotNull String getDeviceFullName() {
        return "%s(%s) [${%s}]".formatted(
                getTitle(),
                getIeeeAddress(),
                defaultIfEmpty(getPlace(), "W.ERROR.PLACE_NOT_SET"));
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return Set.of();
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("rate", "host", "port");
    }

    public void setApplication(String application) {
        setJsonData("app", application);
    }

    public void setFirmware(String value) {
        setJsonData("firmware", value);
    }

    @Override
    public @Nullable String getFirmwareVersion() {
        return "firmware";
    }

    public void setPort(int port) {
        setJsonData("port", port);
    }

    public void setHost(String host) {
        setJsonData("host", host);
    }

    @Override
    public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
        return List.of();
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return optService().map(ChromecastService::getEndpoints).orElse(Map.of());
    }

    public void setChromecastType(ChromecastType type) {
        setJsonData("type", type.toString());
    }

    @RequiredArgsConstructor
    public enum ChromecastType {
        ChromecastAudio, GoogleCastGroup, Chromecast;

        public static ChromecastType findModel(String model) {
            return switch (model) {
                case "Chromecast Audio" -> ChromecastType.ChromecastAudio;
                case "Google Cast Group" -> ChromecastType.GoogleCastGroup;
                default -> ChromecastType.Chromecast;
            };
        }
    }

    @Override
    public void beforePersist() {
        setImageIdentifier(getType() + getChromecastType() + ".png");
    }
}
