package org.touchhome.app.manager.var;

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
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.*;
import java.util.Set;

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

    @Getter
    @MaxItems(30) // max 30 variables in one group
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "workspaceGroup")
    @UIField(order = 30, type = UIFieldType.InlineEntity, style = "height: 100%; padding: 0; background: #1E5E611F;")
    private Set<WorkspaceVariable> workspaceVariables;

    @Lob
    @Getter
    @Column(length = 10_000)
    @Convert(converter = JSONObjectConverter.class)
    private JSONObject jsonData = new JSONObject();

    @Column(unique = true, nullable = false)
    public String groupId;

    @Override
    public String getEntityID() {
        return super.getEntityID();
    }

    @Override
    protected void beforePersist() {
        setEntityID(PREFIX + groupId);
    }
}
