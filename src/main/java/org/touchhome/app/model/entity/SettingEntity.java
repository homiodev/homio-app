package org.touchhome.app.model.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.util.LinkedHashSet;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public class SettingEntity extends BaseEntity<SettingEntity> implements Comparable<SettingEntity> {

    public static final String PREFIX = "st_";

    private String value;

    private String bundle;

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
    private String[] pages;

    @Transient
    private int order;

    @Transient
    private boolean advanced;

    @Transient
    private LinkedHashSet<String> availableValues;

    @Transient
    private String icon;

    @Transient
    private String toggleIcon;

    @Transient
    private SettingPlugin.SettingType settingType;

    public String getValue() {
        return value == null ? defaultValue : value;
    }

    @Override
    public int compareTo(@NotNull SettingEntity o) {
        return Integer.compare(order, o.order);
    }
}
