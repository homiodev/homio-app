package org.homio.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.JSON;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldIconPicker;
import org.homio.api.ui.field.UIFieldProgress;
import org.homio.api.ui.field.UIFieldProgress.Progress;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTitleRef;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIActionInput.Type;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.color.UIFieldColorBgRef;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.inline.UIFieldInlineEditEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.ui.field.inline.UIFieldInlineGroup;
import org.homio.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.UIFieldClickToEdit;
import org.homio.app.model.UIHideEntityIfFieldNotNull;
import org.homio.app.repository.VariableBackupRepository;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

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
@NoArgsConstructor
public class WorkspaceGroup extends BaseEntity
    implements HasJsonData, SelectionParent {

    public static final String PREFIX = "group_";

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
        addRowLabel = "TITLE.CREATE_VAR",
        noContentTitle = "W.ERROR.NO_VARIABLES",
        removeRowCondition = "return !context.get('locked')",
        addRowCondition = "return !context.get('locked')")
    private Set<WorkspaceVariable> workspaceVariables;

    @ManyToOne(fetch = FetchType.EAGER)
    private WorkspaceGroup parent;

    @Getter
    @Column(length = 1000)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    @Column(unique = true, nullable = false)
    private String groupId;

    @Getter
    @JsonIgnore
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "parent")
    private Set<WorkspaceGroup> childrenGroups;

    public WorkspaceGroup(String groupId, String name) {
        this.groupId = groupId;
        setName(name);
    }

    @JsonIgnore
    public WorkspaceGroup getParent() {
        return parent;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @UIField(order = 10, label = "groupName")
    public String getName() {
        return super.getName();
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 9999, hideInEdit = true)
    @UIFieldInlineEntities(bg = "#1E5E611F", noContentTitle = "W.ERROR.NO_VARIABLES")
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
    public void getAllRelatedEntities(@NotNull Set<BaseEntity> set) {
        if (parent != null) {
            set.add(parent);
        }
    }

    @Override
    protected int getChildEntityHashCode() {
        int result = description != null ? description.hashCode() : 0;
        result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        result = 31 * result + (iconColor != null ? iconColor.hashCode() : 0);
        result = 31 * result + jsonData.hashCode();
        return result;
    }

    @Override
    public boolean isDisableDelete() {
        return locked || this.groupId.equals("broadcasts");
    }

    @Override
    public String toString() {
        return "GroupVariable: " + getTitle();
    }

    @UIContextMenuAction(value = "CLEAR_BACKUP", icon = "fas fa-database", inputs = {
        @UIActionInput(name = "KEEP_DAYS", type = Type.number, value = "-1", min = -1, max = 365),
        @UIActionInput(name = "KEEP_COUNT", type = Type.number, value = "-1", min = -1, max = 100_000)
    })
    public ActionResponseModel clearBackup(EntityContext entityContext, JSONObject params) {
        val repository = entityContext.getBean(VariableBackupRepository.class);
        int days = params.optInt("KEEP_DAYS", -1);
        int count = params.optInt("KEEP_COUNT", -1);
        if (days == 0 || count == 0) {
            return clearBackupResponse(clearAll(repository));
        }
        if (days > 0) {
            return clearBackupResponse(clearByDays(days, repository));
        } else if (count > 0) {
            return clearBackupResponse(clearByCount(count, repository));
        }
        return ActionResponseModel.showWarn("W.ERROR.WRONG_ARGUMENTS");
    }

    @Override
    protected void beforePersist() {
        setGroupId(defaultIfEmpty(groupId, CommonUtils.generateUUID()));
        setEntityID(PREFIX + groupId);
        setIcon(defaultIfEmpty(icon, "fas fa-layer-group"));
        setIconColor(defaultIfEmpty(iconColor, "#28A60C"));
        setName(defaultIfEmpty(getName(), CommonUtils.generateUUID()));
    }

    private ActionResponseModel clearBackupResponse(int deletedCount) {
        if (deletedCount > 0) {
            return ActionResponseModel.showSuccess("Deleted: " + deletedCount + " variables");
        }
        return ActionResponseModel.showWarn("W.ERROR.NO_VARIABLES_TO_DELETE");
    }

    private int clearAll(VariableBackupRepository repository) {
        return workspaceVariables.stream().filter(WorkspaceVariable::isBackup)
                                 .map(v -> repository.delete(v.getVariableId()))
                                 .mapToInt(i -> i).sum();
    }

    private int clearByCount(int count, VariableBackupRepository repository) {
        return workspaceVariables.stream().filter(WorkspaceVariable::isBackup)
                                 .map(v -> repository.deleteButKeepCount(v.getVariableId(), count))
                                 .mapToInt(i -> i).sum();
    }

    private int clearByDays(int days, VariableBackupRepository repository) {
        return workspaceVariables.stream().filter(WorkspaceVariable::isBackup)
                                 .map(v -> repository.deleteButKeepDays(v.getVariableId(), days))
                                 .mapToInt(i -> i).sum();
    }

    @Getter
    @NoArgsConstructor
    public static class WorkspaceVariableEntity {

        private String entityID;

        @UIField(order = 10, type = UIFieldType.HTML)
        @UIFieldInlineGroup(value = "return context.get('groupName')", editable = true)
        @UIFieldColorBgRef("color")
        private String groupName;

        @UIField(order = 10, type = UIFieldType.HTML)
        @UIFieldColorRef("color")
        @UIFieldTitleRef("nameTitle")
        @UIFieldVariable
        private JSONObject name;

        @UIField(order = 15, style = "font-size:12px")
        @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
        @UIFieldInlineEntityWidth(12)
        public String value;

        @UIField(order = 20, label = "fmt")
        @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
        @UIFieldInlineEntityWidth(10)
        public String restriction;

        @UIField(order = 30, inlineEdit = true, hideInView = true)
        @UIFieldInlineEntityWidth(12)
        @UIFieldSlider(min = 500, max = 10_000, step = 500)
        public int quota;

        @UIField(order = 40)
        @UIFieldProgress
        @UIFieldInlineEntityWidth(20)
        @UIFieldClickToEdit("quota")
        private Progress usedQuota;

        private String color;

        private String nameTitle;

        public WorkspaceVariableEntity(WorkspaceGroup childrenGroup) {
            this.entityID = childrenGroup.getEntityID();
            this.groupName = format("<div class=\"it-group\"><i class=\"%s\"></i>%s</div>",
                childrenGroup.getIcon(), childrenGroup.getName());
            this.color = childrenGroup.getIconColor();
        }

        public WorkspaceVariableEntity(WorkspaceVariable variable, EntityContextImpl entityContext) {
            this.entityID = variable.getEntityID();

            name = new JSONObject()
                .put("color", variable.getColor())
                .put("name", variable.getName())
                .put("description", variable.getDescription())
                .put("listeners", entityContext.event().getEntityUpdateListeners().getCount(variable.getEntityID()))
                .put("linked", entityContext.var().isLinked(variable.getVariableId()))
                .put("source", entityContext.var().buildDataSource(variable, false))
                .put("readOnly", variable.isReadOnly());
            if (variable.isBackup()) {
                name.put("backupCount", entityContext.var().backupCount(variable.getVariableId()));
            }
            this.restriction = variable.getRestriction().name().toLowerCase();
            Object val = entityContext.var().get(variable.getVariableId());
            this.value = generateValue(val, variable);
            this.quota = variable.getQuota();
            this.usedQuota = variable.getUsedQuota();
            this.nameTitle = variable.getName();
        }

    }

    public static String generateValue(Object val, WorkspaceVariable variable) {
        return val == null ? "-" : val + StringUtils.trimToEmpty(variable.getUnit());
    }
}
