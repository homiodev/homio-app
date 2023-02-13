package org.touchhome.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.springframework.util.ObjectUtils.isEmpty;

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
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.UIHideEntityIfFieldNotNull;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.converter.JSONConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.model.JSON;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldProgress.Progress;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldTitleRef;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorBgRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorRef;
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
@UISidebarMenu(
    icon = "fas fa-boxes-stacked",
    order = 200,
    bg = "#54AD24",
    allowCreateNewItems = true,
    overridePath = "variable")
@AttributeOverride(name = "name", column = @Column(nullable = false))
@UIHideEntityIfFieldNotNull("parent")
public class WorkspaceGroup extends BaseEntity<WorkspaceGroup>
    implements HasJsonData, SelectionParent {

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
    @UIFieldInlineEditEntities(
        bg = "#1E5E611F",
        addRowLabel = "CREATE_VAR",
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
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

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
            EntityContext entityContext = getEntityContext();
            List<WorkspaceVariable> sortedVariables = new ArrayList<>(workspaceVariables);
            sortedVariables.sort(Comparator.comparing(WorkspaceVariable::getName));
            for (WorkspaceVariable workspaceVariable : sortedVariables) {
                list.add(new WorkspaceVariableEntity(workspaceVariable, (EntityContextImpl) entityContext));
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

        @UIField(order = 10, type = UIFieldType.HTML)
        @UIFieldColorRef("color")
        @UIFieldTitleRef("nameTitle")
        private String name;

        @UIField(order = 20, label = "format")
        @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
        @UIFieldInlineEntityWidth(12)
        public String restriction;

        @UIField(order = 30, inlineEdit = true)
        @UIFieldInlineEntityWidth(12)
        @UIFieldSlider(min = 500, max = 10_000, step = 500)
        public int quota;

        @UIField(order = 40, hideInEdit = true)
        @UIFieldProgress
        @UIFieldInlineEntityWidth(20)
        private Progress usedQuota;

        private String color;

        private String nameTitle;

        public WorkspaceVariableEntity(WorkspaceGroup childrenGroup) {
            this.groupName = format("<i style=\"padding: 0 5px;\" class=\"%s\"></i>%s", childrenGroup.getIcon(), childrenGroup.getName());
            this.color = childrenGroup.getIconColor();
        }

        public WorkspaceVariableEntity(WorkspaceVariable variable, EntityContextImpl entityContext) {
            this.entityID = variable.getEntityID();
            String description = isEmpty(variable.getDescription()) ? "" : format("<span>%s</span>", variable.getDescription());
            String preVarNamePart = isEmpty(variable.getColor()) ? "" : format(" style=\"color:%s;\"", variable.getColor());
            int listenerCount = entityContext.event().getEntityUpdateListeners().getCount(variable.getEntityID());
            String meta = variable.isReadOnly() ? "" : format("<i class=\"fas fa-%s\"></i>",
                entityContext.var().isLinked(variable.getVariableId()) ? "link" : "link-slash");

            this.name = format("<div class=\"inline-2row_d\"><div%s>%s<div class=\"info\">%s<span>(%s)</span></div></div>%s</div>",
                preVarNamePart, variable.getName(), meta, listenerCount, description);
            this.restriction = variable.getRestriction().name().toLowerCase();
            this.quota = variable.getQuota();
            this.usedQuota = variable.getUsedQuota();
            this.nameTitle = variable.getName();
        }
    }

    @Override
    public String toString() {
        return "GroupVariable: " + getTitle();
    }
}
