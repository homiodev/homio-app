package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.UserEntity;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingType;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.app.repository.SettingRepository;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "settings")
public class SettingEntity extends BaseEntity implements HasJsonData {

  public static final String PREFIX = "st_";

  @Column(length = 65535)
  private String value;

  @Transient
  private String defaultValue;

  @Transient
  private String groupKey;

  @Transient
  private String groupIcon;

  @Transient
  private String subGroupKey;

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
  private boolean storable;

  @Transient
  private Collection<OptionModel> availableValues;

  @Transient
  private Icon icon;

  @Transient
  private Icon toggleIcon;

  @Transient
  private String settingType;

  @Transient
  private Boolean reverted;

  @Transient
  private Boolean disabled;

  @Transient
  private Boolean required;

  @Transient
  private JSONObject parameters;

  @Transient
  private boolean primary;

  @Transient
  private boolean multiSelect;

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

  public SettingEntity setSettingType(@NotNull SettingType settingType) {
    if (this.settingType == null) {
      this.settingType = settingType.name();
    }
    return this;
  }

  public String getValue() {
    UserEntity user = context().user().getLoggedInUser();
    String value = this.value;
    if (user != null && !user.isAdmin()) {
      value = defaultIfEmpty(getJsonData(user.getEmail()), this.value);
    }
    return defaultIfEmpty(value, defaultValue);
  }

  public SettingEntity setValue(String value) {
    UserEntity user = context().user().getLoggedInUser();
    if (user != null && !user.isAdmin()) {
      setJsonData(user.getEmail(), value);
    }
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
  protected long getChildEntityHashCode() {
    return 0;
  }

  @Override
  public @NotNull String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  public String getAddonID() {
    // dynamic settings(firmata has no parameters)
    SettingPlugin plugin = ContextSettingImpl.settingPluginsByPluginKey.get(getEntityID());
    return plugin == null ? null : SettingRepository.getSettingAddonName(context(), plugin.getClass());
  }

  @Override
  @JsonIgnore
  public boolean isDisableDelete() {
    return super.isDisableDelete();
  }

  @Override
  @JsonIgnore
  public @NotNull String getTitle() {
    return super.getTitle();
  }
}
