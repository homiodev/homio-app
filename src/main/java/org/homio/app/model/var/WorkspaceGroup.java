package org.homio.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.app.utils.UIFieldUtils.nullIfFalse;

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
import org.homio.api.Context;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.state.DecimalType;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldIconPicker;
import org.homio.api.ui.field.UIFieldProgress;
import org.homio.api.ui.field.UIFieldProgress.Progress;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIActionInput.Type;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.color.UIFieldColorBgRef;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.ui.field.inline.UIFieldInlineGroup;
import org.homio.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextVarImpl;
import org.homio.app.model.UIFieldClickToEdit;
import org.homio.app.model.UIHideEntityIfFieldNotNull;
import org.homio.app.model.var.WorkspaceVariable.VarType;
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

    @UIField(order = 15, hideInEdit = true)
    public int getVarCount() {
        if (workspaceVariables == null) {return 0;}
        return workspaceVariables.size() +
            (childrenGroups == null ? 0 : childrenGroups
                .stream()
                .map(WorkspaceGroup::getVarCount)
                .reduce(0, Integer::sum));
    }

    @Getter
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "workspaceGroup")
    @JsonIgnore
    private Set<WorkspaceVariable> workspaceVariables;

    @ManyToOne(fetch = FetchType.EAGER)
    private WorkspaceGroup parent;

    @Getter
    @Column(length = 1000)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    @Getter
    @JsonIgnore
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "parent")
    private Set<WorkspaceGroup> childrenGroups;

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
            Context context = context();
            List<WorkspaceVariable> sortedVariables = new ArrayList<>(workspaceVariables);
            sortedVariables.sort(Comparator.comparing(WorkspaceVariable::getName));
            for (WorkspaceVariable workspaceVariable : sortedVariables) {
                list.add(new WorkspaceVariableEntity(workspaceVariable, (ContextImpl) context));
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
    public boolean isDisableDelete() {
        return locked || getEntityID().equals(PREFIX + "broadcasts") || getJsonData("dis_del", false);
    }

    public static String generateValue(Object val, WorkspaceVariable variable) {
        if (val instanceof Number num) {
            return new DecimalType(num).toString(2) + StringUtils.trimToEmpty(variable.getUnit());
        }
        return val == null ? "-" : val + StringUtils.trimToEmpty(variable.getUnit());
    }

    @Override
    public String toString() {
        return "GroupVariable: " + getTitle();
    }

    @UIContextMenuAction(value = "CLEAR_BACKUP", icon = "fas fa-database", inputs = {
            @UIActionInput(name = "keepDays", type = Type.number, value = "-1", min = -1, max = 365),
            @UIActionInput(name = "keepCount", type = Type.number, value = "-1", min = -1, max = 100_000)
    })
    public ActionResponseModel clearBackup(Context context, JSONObject params) {
        val repository = context.getBean(VariableBackupRepository.class);
        int days = params.optInt("keepDays", -1);
        int count = params.optInt("keepCount", -1);
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
    public void beforePersist() {
        setIcon(defaultIfEmpty(icon, "fas fa-layer-group"));
        setIconColor(defaultIfEmpty(iconColor, "#28A60C"));
    }

    @Override
    protected long getChildEntityHashCode() {
        int result = description != null ? description.hashCode() : 0;
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        result = 31 * result + (iconColor != null ? iconColor.hashCode() : 0);
        result = 31 * result + (locked ? 1 : 0);
        result = 31 * result + jsonData.toString().hashCode();
        return result;
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }

    private int clearAll(VariableBackupRepository repository) {
        return workspaceVariables.stream().filter(WorkspaceVariable::isBackup)
                                 .map(repository::delete)
                .mapToInt(i -> i).sum();
    }

    private int clearByCount(int count, VariableBackupRepository repository) {
        return workspaceVariables.stream().filter(WorkspaceVariable::isBackup)
                .map(v -> repository.deleteButKeepCount(v, count))
                .mapToInt(i -> i).sum();
    }

    private int clearByDays(int days, VariableBackupRepository repository) {
        return workspaceVariables.stream().filter(WorkspaceVariable::isBackup)
                .map(v -> repository.deleteButKeepDays(v, days))
                .mapToInt(i -> i).sum();
    }

    private ActionResponseModel clearBackupResponse(int deletedCount) {
        if (deletedCount > 0) {
            return ActionResponseModel.showSuccess("Deleted: " + deletedCount + " variables");
        }
        return ActionResponseModel.showInfo("W.ERROR.NO_VARIABLES_TO_DELETE");
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

        private Boolean disableDelete;

        private String rowClass;

        private Boolean backup;

        public WorkspaceVariableEntity(WorkspaceGroup childrenGroup) {
            this.entityID = childrenGroup.getEntityID();
            this.groupName = format("<div class=\"it-group\"><i class=\"%s\"></i>%s</div>",
                    childrenGroup.getIcon(), childrenGroup.getName());
            this.color = childrenGroup.getIconColor();
        }

        public WorkspaceVariableEntity(WorkspaceVariable variable, ContextImpl context) {
            this.entityID = variable.getEntityID();

            name = new JSONObject()
                .put("color", variable.getIconColor())
                    .put("name", variable.getName())
                    .put("description", variable.getDescription())
                .put("listeners", context.event().getEventCount(variable.getEntityID()))
                .put("linked", context.var().isLinked(variable.getEntityID()))
                .put("source", ContextVarImpl.buildDataSource(variable))
                    .put("readOnly", variable.isReadOnly());
            if (variable.isBackup()) {
                name.put("backupCount", context.var().backupCount(variable));
            }
            if (variable.getIcon() != null) {
                name.put("icon", new Icon(variable.getIcon(), variable.getIconColor()));
            }
            this.restriction = variable.getRestriction().name().toLowerCase();
            Object val = context.var().getRawValue(variable.getEntityID());
            this.value = generateValue(val, variable);
            this.backup = nullIfFalse(variable.isBackup());
            this.quota = variable.getQuota();
            this.usedQuota = variable.getUsedQuota();
            this.disableDelete = Boolean.TRUE.equals(variable.isDisableDelete()) ? true : null;
            if (variable.getVarType() != VarType.standard) {
                this.rowClass = "var-type-%s".formatted(variable.getVarType());
            }
        }

        public static WorkspaceVariableEntity updatableEntity(WorkspaceVariable variable, ContextImpl context) {
            WorkspaceVariableEntity entity = new WorkspaceVariableEntity();
            Object val = context.var().getRawValue(variable.getEntityID());
            entity.entityID = variable.getEntityID();
            entity.value = generateValue(val, variable);
            entity.usedQuota = variable.getUsedQuota();
            return entity;
        }

    }
}
