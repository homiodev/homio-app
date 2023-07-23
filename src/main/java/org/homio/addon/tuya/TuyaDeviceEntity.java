package org.homio.addon.tuya;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-gamepad", color = "#0088CC")
public final class TuyaDeviceEntity extends MiscEntity<TuyaDeviceEntity>
    implements HasStatusAndMsg<TuyaDeviceEntity> {

    public static final String PREFIX = "tuyad_";

    @UIField(order = 30, required = true, inlineEditWhenEmpty = true)
    public String getDeviceID() {
        return getJsonData("deviceID");
    }

    public void setDeviceID(String value) {
        setJsonData("deviceID", value);
    }

    @UIField(order = 35, required = true, inlineEditWhenEmpty = true)
    public String getLocalKey() {
        return getJsonData("localKey");
    }

    public void setLocalKey(String value) {
        setJsonData("localKey", value);
    }

    @UIField(order = 35, required = true, disableEdit = true, type = UIFieldType.IpAddress)
    public String getIp() {
        return getJsonData("ip");
    }

    public void setIp(String value) {
        setJsonData("ip", value);
    }

    @UIField(order = 6)
    public ProtocolVersion getProtocolVersion() {
        return getJsonDataEnum("pv", ProtocolVersion.P34);
    }

    public void setProtocolVersion(ProtocolVersion value) {
        setJsonData("pv", value);
    }

    @UIField(order = 5)
    @UIFieldSlider(min = 10, max = 60, header = "s", extraValue = "0")
    public int getPollingInterval() {
        return getJsonData("pi", 0);
    }

    public void setPollingInterval(int value) {
        setJsonData("pi", value);
    }

    @Override
    public String getDefaultName() {
        return "Generic Tuya Device";
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @RequiredArgsConstructor
    public enum ProtocolVersion implements KeyValueEnum {
        P31("3.1"),
        P33("3.3"),
        P34("3.4");

        private final String value;

        @Override
        public String getKey() {
            return value;
        }

        @Override
        public String getValue() {
            return name();
        }
    }
}
