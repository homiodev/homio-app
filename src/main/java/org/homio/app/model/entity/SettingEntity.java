package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.JSON;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.app.repository.SettingRepository;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public class SettingEntity extends BaseEntity<SettingEntity> {

    public static final String PREFIX = "st_";

    @Column(length = 65535)
    private String value;

    @Transient private String color;

    @Transient private String defaultValue;

    @Transient private String groupKey;

    @Transient private String groupIcon;

    @Transient private String subGroupKey;

    @Transient private boolean visible;

    @Transient private Set<String> pages;

    @Transient private Set<ConsolePlugin.RenderType> renderTypes;

    @Transient private int order;

    @Transient private boolean advanced;

    @Transient private boolean lazyLoad;

    @Transient private boolean storable;

    @Transient private Collection<OptionModel> availableValues;

    @Transient private String icon;

    @Transient private String toggleIcon;

    @Transient private String settingType;

    @Transient private Boolean reverted;

    @Transient private Boolean disabled;

    @Transient private Boolean required;

    @Transient private JSONObject parameters;

    @Transient private boolean primary;

    @Getter
    @JsonIgnore
    @Column(length = 65535)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    @Override
    @JsonIgnore
    public @NotNull Date getUpdateTime() {
        return super.getUpdateTime();
    }

    @Override
    @JsonIgnore
    public @NotNull Date getCreationTime() {
        return super.getCreationTime();
    }

    public static String getKey(SettingPlugin settingPlugin) {
        if (settingPlugin instanceof DynamicConsoleHeaderSettingPlugin) {
            return SettingEntity.PREFIX
                + ((DynamicConsoleHeaderSettingPlugin) settingPlugin).getKey();
        }
        return SettingEntity.PREFIX + settingPlugin.getClass().getSimpleName();
    }

    public static String getKey(Class<? extends SettingPlugin> settingPluginClazz) {
        return SettingEntity.PREFIX + settingPluginClazz.getSimpleName();
    }

    public void setSettingTypeRaw(String settingTypeRaw) {
        this.settingType = settingTypeRaw;
    }

    public SettingEntity setSettingType(UIFieldType settingType) {
        this.settingType = settingType.name();
        return this;
    }

    public String getValue() {
        return StringUtils.defaultIfEmpty(value, defaultValue);
    }

    public SettingEntity setValue(String value) {
        this.value = value;
        return this;
    }

    @Override
    public int compareTo(@NotNull BaseEntity o) {
        if (o instanceof SettingEntity) {
            return Integer.compare(order, ((SettingEntity) o).order);
        }
        return super.compareTo(o);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public String getAddonID() {
        // dynamic settings(firmata has no parameters)
        SettingPlugin plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(getEntityID());
        return plugin == null ? null : SettingRepository.getSettingAddonName(getEntityContext(), plugin.getClass());
    }
}
