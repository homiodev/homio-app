package org.touchhome.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.converter.JSONConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.model.JSON;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.touchhome.bundle.api.ui.field.UIFieldType;

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

    @Getter
    @JsonIgnore
    @Column(length = 65535)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

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
    public void afterFetch(EntityContext entityContext) {
        SettingRepository.fulfillEntityFromPlugin(this, entityContext, null);
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
    public String getBundle() {
        // dynamic settings(firmata has no parameters)
        SettingPlugin plugin =
            EntityContextSettingImpl.settingPluginsByPluginKey.get(getEntityID());
        return plugin == null
            ? null
            : SettingRepository.getSettingBundleName(getEntityContext(), plugin.getClass());
    }
}
