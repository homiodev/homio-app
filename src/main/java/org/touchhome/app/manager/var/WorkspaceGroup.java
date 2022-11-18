package org.touchhome.app.manager.var;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import java.util.Set;
import javax.persistence.AttributeOverride;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldInlineEntity;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Lang;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UISidebarMenu(icon = "fas fa-boxes-stacked", order = 200, bg = "#54AD24",
    allowCreateNewItems = true, overridePath = "variable")
@AttributeOverride(name = "name", column = @Column(nullable = false, unique = true))
public class WorkspaceGroup extends BaseEntity<WorkspaceGroup> implements HasJsonData {

  public static final String PREFIX = "wg_";
  @UIField(order = 12, inlineEditWhenEmpty = true)
  private String description;
  // unable to CRUD variables inside group or rename/drop group
  private boolean locked;
  @UIField(order = 13)
  @UIFieldIconPicker(allowSize = false, allowSpin = false)
  private String icon;
  @UIField(order = 14)
  @UIFieldColorPicker
  private String iconColor;
  @Getter
  @MaxItems(100) // max 100 variables in one group
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "workspaceGroup")
  @UIField(order = 30)
  @UIFieldInlineEntity(bg = "#1E5E611F", addRow = "CREATE_VAR",
      noContentTitle = "NO_VARIABLES",
      removeRowCondition = "return !context.get('locked')",
      addRowCondition = "return !context.get('locked')")
  private Set<WorkspaceVariable> workspaceVariables;

  @Getter
  @Column(length = 1000)
  @Convert(converter = JSONObjectConverter.class)
  private JSONObject jsonData = new JSONObject();

  @Column(unique = true, nullable = false)
  private String groupId;

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  @UIField(order = 10, label = "groupName")
  public String getName() {
    return super.getName();
  }

  @Override
  public String getEntityID() {
    return super.getEntityID();
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  @JsonIgnore
  @UIFieldIgnore
  public Date getCreationTime() {
    return super.getCreationTime();
  }

  @Override
  @JsonIgnore
  @UIFieldIgnore
  public Date getUpdateTime() {
    throw new ProhibitedExecution();
  }

  @Override
  protected void beforePersist() {
    setGroupId(defaultIfEmpty(groupId, CommonUtils.generateUUID()));
    setEntityID(PREFIX + groupId);
    setIcon(defaultIfEmpty(icon, "fas fa-layer-group"));
    setIconColor(defaultIfEmpty(iconColor, "#28A60C"));
    setName(defaultIfEmpty(getName(), CommonUtils.generateUUID()));
  }

  public String getDescription() {
    if (description == null) {
      return Lang.getServerMessageOptional("description." + getGroupId()).orElse(null);
    }
    return description;
  }
}
