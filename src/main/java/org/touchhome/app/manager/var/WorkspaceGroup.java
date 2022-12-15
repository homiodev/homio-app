package org.touchhome.app.manager.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.persistence.AttributeOverride;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;
import org.touchhome.app.model.UIHideEntityIfFieldNotNull;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldProgress.Progress;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorBgRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorSource;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEditEntities;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntities;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;
import org.touchhome.common.util.CommonUtils;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UISidebarMenu(icon = "fas fa-boxes-stacked", order = 200, bg = "#54AD24",
               allowCreateNewItems = true, overridePath = "variable")
@AttributeOverride(name = "name", column = @Column(nullable = false))
@UIHideEntityIfFieldNotNull("parent")
public class WorkspaceGroup extends BaseEntity<WorkspaceGroup> implements HasJsonData, SelectionParent {

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
  private boolean hidden;
  @Getter
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "workspaceGroup")
  @UIField(order = 30)
  @UIFieldInlineEditEntities(bg = "#1E5E611F", addRowLabel = "CREATE_VAR",
                             noContentTitle = "NO_VARIABLES",
                             removeRowCondition = "return !context.get('locked')",
                             addRowCondition = "return !context.get('locked')")
  private Set<WorkspaceVariable> workspaceVariables;

  @ManyToOne(fetch = FetchType.EAGER)
  private WorkspaceGroup parent;

  @JsonIgnore
  public WorkspaceGroup getParent() {
    return parent;
  }

  @Getter
  @Column(length = 1000)
  @Convert(converter = JSONObjectConverter.class)
  private JSONObject jsonData = new JSONObject();

  @Column(unique = true, nullable = false)
  private String groupId;

  @Getter
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "parent")
  private Set<WorkspaceGroup> childrenGroups;

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

  @UIField(order = 9999, hideInEdit = true)
  @UIFieldInlineEntities(bg = "#1E5E611F", noContentTitle = "NO_VARIABLES")
  public List<WorkspaceVariableEntity> getWorkspaceVariableEntities() {
    List<WorkspaceVariableEntity> list = new ArrayList<>();
    if (childrenGroups != null) {
      List<WorkspaceGroup> sortedGroups = new ArrayList<>(childrenGroups);
      sortedGroups.sort(Comparator.comparing(WorkspaceGroup::getName));
      for (WorkspaceGroup childrenGroup : sortedGroups) {
        list.add(new WorkspaceVariableEntity(childrenGroup));
        list.addAll(childrenGroup.getWorkspaceVariableEntities());
      }
    }
    if (workspaceVariables != null) {
      List<WorkspaceVariable> sortedVariables = new ArrayList<>(workspaceVariables);
      sortedVariables.sort(Comparator.comparing(WorkspaceVariable::getName));
      for (WorkspaceVariable workspaceVariable : sortedVariables) {
        list.add(new WorkspaceVariableEntity(workspaceVariable));
      }
    }
    return list;
  }

  @Override
  public String getParentId() {
    return getEntityID();
  }

  @Override
  public String getParentName() {
    return getName();
  }

  @Override
  public String getParentIcon() {
    return getIcon();
  }

  @Override
  public String getParentIconColor() {
    return getIconColor();
  }

  @Override
  public void getAllRelatedEntities(Set<BaseEntity> set) {
    if (parent != null) {
      set.add(parent);
    }
  }

  @Override
  public boolean isDisableEdit() {
    return locked;
  }

  @Override
  public boolean isDisableDelete() {
    return locked;
  }

  @Getter
  @NoArgsConstructor
  public static class WorkspaceVariableEntity {

    private String entityID;

    @UIField(order = 10, type = UIFieldType.HTML)
    @UIFieldInlineGroup("return context.get('groupName')")
    @UIFieldColorBgRef("color")
    private String groupName;

    @UIField(order = 10)
    @UIFieldColorRef("color")
    @UIFieldInlineEntityWidth(25)
    private String name;

    @UIField(order = 15, color = "#7A7A7A")
    public String description;

    @UIField(order = 20, label = "format")
    @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
    @UIFieldInlineEntityWidth(15)
    public EntityContextVar.VariableType restriction;

    @UIField(order = 30)
    @UIFieldInlineEntityWidth(14)
    public int quota;


    @UIField(order = 40, hideInEdit = true)
    @UIFieldProgress
    @UIFieldInlineEntityWidth(20)
    private Progress usedQuota;

    @UIFieldColorSource
    public String color;

    public WorkspaceVariableEntity(WorkspaceGroup childrenGroup) {
      this.groupName = format("<i style=\"padding: 0 5px;\" class=\"%s\"></i>Group: %s", childrenGroup.getIcon(), childrenGroup.getName());
      this.color = childrenGroup.getIconColor();
    }

    public WorkspaceVariableEntity(WorkspaceVariable workspaceVariable) {
      this.entityID = workspaceVariable.getEntityID();
      this.name = workspaceVariable.getName();
      this.description = workspaceVariable.getDescription();
      this.restriction = workspaceVariable.getRestriction();
      this.quota = workspaceVariable.getQuota();
      this.usedQuota = workspaceVariable.getUsedQuota();
    }
  }
}
