package org.touchhome.app.model.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;

import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Set;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public class SettingEntity extends BaseEntity<SettingEntity> implements Comparable<SettingEntity> {

    public static final String PREFIX = "st_";

    private String value;

    @Transient
    private String color;

    @Transient
    private String defaultValue;

    @Transient
    private String groupKey;

    @Transient
    private String groupIcon;

    @Transient
    private String subGroupKey;

    @Transient
    private String bundle;

    @Transient
    private boolean visible;

    @Transient
    private Set<String> pages;

    @Transient
    private Set<ConsolePlugin.RenderType> renderTypes;

    @Transient
    private int order;

    @Transient
    private boolean advanced;

    @Transient
    private Collection<Option> availableValues;

    @Transient
    private String icon;

    @Transient
    private String toggleIcon;

    @Transient
    private SettingPlugin.SettingType settingType;

    @Transient
    private Boolean reverted;

    @Transient
    private Boolean disabled;

    @Transient
    private Boolean required;

    @Transient
    private JSONObject parameters;

    public static String getKey(BundleSettingPlugin settingPlugin) {
        return SettingEntity.PREFIX + settingPlugin.getClass().getSimpleName();
    }

    public static String getKey(Class<? extends BundleSettingPlugin> settingPluginClazz) {
        return SettingEntity.PREFIX + settingPluginClazz.getSimpleName();
    }

    public String getValue() {
        return StringUtils.defaultIfEmpty(value, defaultValue);
    }

    @Override
    public int compareTo(@NotNull SettingEntity o) {
        return Integer.compare(order, o.order);
    }
}
