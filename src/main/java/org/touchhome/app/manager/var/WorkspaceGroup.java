package org.touchhome.app.manager.var;

import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldInlineEntity;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UISidebarMenu(icon = "fas fa-boxes-stacked", order = 200, bg = "#54AD24",
    allowCreateNewItems = true, overridePath = "variable")
public class WorkspaceGroup extends BaseEntity<WorkspaceGroup> implements HasJsonData {

  public static final String PREFIX = "wg_";

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  @UIField(order = 10, label = "groupName")
  public String getName() {
    return super.getName();
  }

  @UIField(order = 12)
  private String description;

  // unable to CRUD variables inside group or rename/drop group
  private boolean locked;

  @Getter
  @MaxItems(30) // max 30 variables in one group
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "workspaceGroup")
  @UIField(order = 30)
  @UIFieldInlineEntity(bg = "#1E5E611F", addRow = "CREATE_VAR")
  private Set<WorkspaceVariable> workspaceVariables;

  @Lob
  @Getter
  @Column(length = 10_000)
  @Convert(converter = JSONObjectConverter.class)
  private JSONObject jsonData = new JSONObject();

  @Column(unique = true, nullable = false)
  private String groupId;

  @Override
  public String getEntityID() {
    return super.getEntityID();
  }

  @Override
  protected void beforePersist() {
    setEntityID(PREFIX + groupId);
  }
}
