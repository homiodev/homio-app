package org.homio.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.api.ContextVar.GROUP_BROADCAST;
import static org.homio.api.ui.UI.Color.RED;
import static org.homio.api.ui.field.UIFieldType.HTML;
import static org.homio.api.ui.field.action.ActionInputParameter.select;
import static org.homio.api.ui.field.action.ActionInputParameter.textRequired;
import static org.homio.app.utils.UIFieldUtils.nullIfFalse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextUI;
import org.homio.api.ContextVar;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.model.OptionModel;
import org.homio.api.state.DecimalType;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.UIFieldProgress.Progress;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIActionInput.Type;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.api.ui.field.color.UIFieldColorBgRef;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.ui.field.inline.UIFieldInlineGroup;
import org.homio.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.ui.route.UIRouteMenu;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextVarImpl;
import org.homio.app.model.UIFieldClickToEdit;
import org.homio.app.model.UIHideEntityIfFieldNotNull;
import org.homio.app.model.var.WorkspaceVariable.VarType;
import org.homio.app.repository.VariableBackupRepository;
import org.homio.app.setting.WorkspaceGroupEntityCompactModeSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UIRouteMenu(
    order = 20,
    icon = "fas fa-boxes-stacked",
    color = "#54AD24",
    allowCreateItem = true,
    overridePath = "variable",
    filter = {"*:fas fa-filter:#8DBA73"})
@AttributeOverride(name = "name", column = @Column(nullable = false))
@UIHideEntityIfFieldNotNull("parent")
@NoArgsConstructor
@Table(name = "workspace_group")
public class WorkspaceGroup extends BaseEntity
    implements HasJsonData, SelectionParent, HasDynamicContextMenuActions {

  public static final String PREFIX = "group_";
  public static final String BROADCAST_GROUP = PREFIX + "broadcast";

  @UIField(order = 12, inlineEditWhenEmpty = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  private String description;

  // unable to CRUD variables inside group or rename/drop group
  private boolean locked;

  @UIField(order = 13)
  @UIFieldIconPicker(allowSize = false, allowSpin = false)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  private String icon;

  @UIField(order = 14)
  @UIFieldColorPicker
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  private String iconColor;

  private boolean hidden;

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

  public static String generateValue(Object val, WorkspaceVariable variable) {
    if (val instanceof Number num) {
      return new DecimalType(num).toString(2) + StringUtils.trimToEmpty(variable.getUnit());
    }
    return val == null ? "-" : val + StringUtils.trimToEmpty(variable.getUnit());
  }

  public boolean isCompactMode() {
    return context().setting().getValue(WorkspaceGroupEntityCompactModeSetting.class);
  }

  @UIField(
      order = 1,
      fullWidth = true,
      color = "#89AA50",
      type = HTML,
      style = "height: 32px;",
      hideInEdit = true)
  @UIFieldShowOnCondition("return context.get('compactMode')")
  public String getCompactDescription() {
    if (!isCompactMode()) {
      return null;
    }
    return format(
        "<i style=\"color:%s\" class=\"%s\"></i> %s (%s)",
        getIconColor(), getIcon(), getName(), getVarCount());
  }

  @UIField(order = 15, hideInEdit = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  public int getVarCount() {
    if (workspaceVariables == null) {
      return 0;
    }
    return workspaceVariables.size()
        + (childrenGroups == null
            ? 0
            : childrenGroups.stream().map(WorkspaceGroup::getVarCount).reduce(0, Integer::sum));
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
  @UIFieldShowOnCondition("return !context.get('compactMode')")
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
    return locked
        || getEntityID().equals(PREFIX + GROUP_BROADCAST)
        || getJsonData("dis_del", false)
        || super.isDisableDelete();
  }

  @Override
  public String toString() {
    return "GroupVariable: " + getTitle();
  }

  @UIContextMenuAction(
      value = "CLEAR_BACKUP",
      icon = "fas fa-trash",
      iconColor = RED,
      inputs = {
        @UIActionInput(name = "description", type = Type.info, value = "TITLE.CLEAR_BACKUP_DESCR"),
        @UIActionInput(name = "keepDays", type = Type.number, value = "-1", min = -1, max = 365),
        @UIActionInput(
            name = "keepCount",
            type = Type.number,
            value = "-1",
            min = -1,
            max = 100_000)
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

  private int clearAll(VariableBackupRepository repository) {
    return workspaceVariables.stream()
        .filter(WorkspaceVariable::isBackup)
        .map(repository::delete)
        .mapToInt(i -> i)
        .sum();
  }

  private int clearByCount(int count, VariableBackupRepository repository) {
    return workspaceVariables.stream()
        .filter(WorkspaceVariable::isBackup)
        .map(v -> repository.deleteButKeepCount(v, count))
        .mapToInt(i -> i)
        .sum();
  }

  private int clearByDays(int days, VariableBackupRepository repository) {
    return workspaceVariables.stream()
        .filter(WorkspaceVariable::isBackup)
        .map(v -> repository.deleteButKeepDays(v, days))
        .mapToInt(i -> i)
        .sum();
  }

  private ActionResponseModel clearBackupResponse(int deletedCount) {
    if (deletedCount > 0) {
      return ActionResponseModel.showSuccess("Deleted: " + deletedCount + " variables");
    }
    return ActionResponseModel.showInfo("W.ERROR.NO_VARIABLES_TO_DELETE");
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {
    uiInputBuilder
        .addOpenDialogSelectableButton(
            "ADD_GROUP",
            new Icon("fas fa-layer-group"),
            (context, params) ->
                ((ContextImpl) context).var().createSubGroup(params, getEntityID()))
        .editDialog(
            dialogBuilder -> addCommonFields(dialogBuilder, "field.groupName", builder -> {}));

    if (this.getEntityID().equals(BROADCAST_GROUP)) {
      uiInputBuilder
          .addOpenDialogSelectableButton(
              "ADD_BROADCAST_VARIABLE",
              new Icon("fas fa-rss"),
              (context, params) ->
                  createVar(
                      context, params, ContextVar.VariableType.Broadcast, true, getEntityID()))
          .editDialog(this::buildBroadcastDialog);
    } else {
      uiInputBuilder.addOpenDialogSelectableButtonFromClass(
          "ADD_VARIABLE",
          new Icon("fas fa-rss"),
          CreateGroupVariableDialogModel.class,
          (context, params) -> {
            var variableType = params.getEnum(ContextVar.VariableType.class, "variableType");
            return createVar(context, params, variableType, false, getEntityID());
          });
    }
  }

  private void buildBroadcastDialog(UIDialogLayoutBuilder dialogBuilder) {
    addCommonFields(
        dialogBuilder,
        "field.broadcastName",
        builder ->
            builder
                .addSelectBox("subGroup")
                .setRequired(true)
                .setValue(getEntityID())
                .setOptions(OptionModel.entityList(this.getChildrenGroups(), null, context())));
  }

  public static void addCommonFields(
      UIDialogLayoutBuilder dialogBuilder,
      String nameFieldKey,
      Consumer<UIFlexLayoutBuilder> builder) {
    dialogBuilder.addFlex(
        "main",
        flex -> {
          flex.addTextInput("name", Lang.getServerMessage(nameFieldKey), true);
          flex.addTextInput("description", "", false);
          flex.addIconPicker("icon", "fas fa-cloud");
          flex.addColorPicker("color", "#999999");
          builder.accept(flex);
        });
  }

  public static @NotNull ActionResponseModel createVar(
      Context context,
      JSONObject params,
      ContextVar.VariableType variableType,
      boolean readOnly,
      @Nullable String defaultGroup) {
    String name = params.getString("name");
    String group = params.optString("subGroup");
    if (StringUtils.isEmpty(group)) {
      group = params.optString("variableGroup");
      if (StringUtils.isEmpty(group)) {
        group = defaultGroup;
        if (StringUtils.isEmpty(group)) {
          throw new IllegalStateException("Unable to evaluate variable group");
        }
      }
    }
    if (context.var().exists(name)) {
      return ActionResponseModel.showError("ERROR.EXISTS_VARIABLE", name);
    }
    context
        .var()
        .createVariable(
            group,
            name,
            name,
            variableType,
            builder -> {
              builder.setWritable(!readOnly).setDescription(params.optString("description"));
              String icon = params.optString("icon");
              if (isNotEmpty(icon)) {
                builder.setIcon(new Icon(icon, params.optString("color", "#999999")));
              }
              if (params.has("min") && params.has("max")) {
                builder.setRange(params.getFloat("min"), params.getFloat("max"));
              }
            });
    return ActionResponseModel.showSuccess("W.SUCCESS.VARIABLE");
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class CreateGeneralVariableDialogModel extends BaseVariableDialogModel {
    @UIField(order = 6, required = true)
    @UIFieldDynamicSelection(VariableGroupSelection.class)
    public String variableGroup;

    public CreateGeneralVariableDialogModel(String variableGroup) {
      this.variableGroup = variableGroup;
    }
  }

  @Getter
  @Setter
  public static class CreateGroupVariableDialogModel extends BaseVariableDialogModel {
    @UIField(order = 5)
    @UIFieldDynamicSelection(SubGroupSelection.class)
    public String subGroup;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public abstract static class BaseVariableDialogModel {
    @UIField(order = 1)
    public ContextVar.VariableType variableType = ContextVar.VariableType.Float;

    @UIField(order = 2, required = true)
    public String name = "Variable name";

    @UIField(order = 3)
    @UIFieldIconPicker(allowSize = false, allowSpin = false)
    public String icon = "fas fa-cloud";

    @UIField(order = 4)
    @UIFieldColorPicker
    public String iconColor = "#999999";

    @UIField(order = 9)
    @UIFieldShowOnCondition("return context.get('variableType') == 'Float'")
    private int min;

    @UIField(order = 10)
    @UIFieldShowOnCondition("return context.get('variableType') == 'Float'")
    private int max = 100;

    @UIField(order = 11)
    public String description = "Variable description";
  }

  @Getter
  @NoArgsConstructor
  public static class WorkspaceVariableEntity implements UIFieldInlineEntities.InlineEntity {

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

    @JsonIgnore private WorkspaceVariable variable;
    private @Nullable Float min;
    private @Nullable Float max;
    private String entityID;

    @UIField(order = 10, type = UIFieldType.HTML)
    @UIFieldInlineGroup(value = "return context.get('groupName')", editable = true)
    @UIFieldColorBgRef("color")
    private String groupName;

    @UIField(order = 10, type = UIFieldType.HTML)
    @UIFieldColorRef("color")
    @UIFieldVariable
    private JSONObject name;

    @UIField(order = 40)
    @UIFieldProgress
    @UIFieldInlineEntityWidth(20)
    @UIFieldClickToEdit("quota")
    private Progress usedQuota;

    private String color;

    private Boolean disableDelete;

    private String rowClass;

    private Boolean backup;

    private String rawName;

    public WorkspaceVariableEntity(WorkspaceGroup childrenGroup) {
      this.entityID = childrenGroup.getEntityID();
      this.groupName =
          format(
              "<div class=\"it-group\"><i class=\"%s\"></i>%s</div>",
              childrenGroup.getIcon(), childrenGroup.getName());
      this.rawName = childrenGroup.getName();
      this.color = childrenGroup.getIconColor();
    }

    public WorkspaceVariableEntity(WorkspaceVariable variable, ContextImpl context) {
      this.entityID = variable.getEntityID();
      this.variable = variable;

      name =
          new JSONObject()
              .put("color", variable.getIconColor())
              .put("name", variable.getName())
              .put("description", variable.getDescription())
              .put("listeners", context.event().getEventCount(variable.getFullEntityID()))
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
      this.min = variable.getMin();
      this.max = variable.getMax();
      this.disableDelete = Boolean.TRUE.equals(variable.isDisableDelete()) ? true : null;
      if (variable.getVarType() != VarType.standard) {
        this.rowClass = "var-type-%s".formatted(variable.getVarType());
      }
    }

    public static WorkspaceVariableEntity updatableEntity(
        WorkspaceVariable variable, ContextImpl context) {
      WorkspaceVariableEntity entity = new WorkspaceVariableEntity();
      Object val = context.var().getRawValue(variable.getEntityID());
      entity.entityID = variable.getEntityID();
      entity.value = generateValue(val, variable);
      entity.usedQuota = variable.getUsedQuota();
      return entity;
    }

    @UIContextMenuAction(
        value = "CLEAR_ALL_BACKUP",
        icon = "fas fa-trash",
        iconColor = RED,
        confirmMessage = "W.CONFIRM.CLEAR_BACKUP_WARN")
    public ActionResponseModel clearBackup(Context context) {
      val repository = context.getBean(VariableBackupRepository.class);
      repository.delete(variable);
      context.ui().updateItem(variable.getTopGroup());
      return ActionResponseModel.success();
    }

    @UIContextMenuAction(
        value = "TITLE.CREATE_DISPLAY_WIDGET",
        icon = "fas fa-chart-simple",
        iconColor = "#2DA3A4")
    public ActionResponseModel createDisplayWidget(Context context) {
      context
          .ui()
          .dialog()
          .sendDialogRequest(
              "create-display-widget",
              "TITLE.CREATE_DISPLAY_WIDGET",
              (responseType, pressedButton, parameters) ->
                  context
                      .widget()
                      .createDisplayWidget(
                          variable.getEntityID(),
                          builder -> {
                            builder
                                .setChartHeight(40)
                                .setLineBorderWidth(1)
                                .setChartPointsPerHour(120)
                                .setShowChartFullScreenButton(true)
                                .setChartDataSource(variable.getFullEntityID())
                                .setChartColor(variable.getIconColor())
                                .attachToTab(parameters.get("tab").asText());
                            builder.addSeries(
                                parameters.get("name").asText(),
                                series ->
                                    series
                                        .setValueSuffix(variable.getUnit())
                                        .setValueDataSource(variable.getFullEntityID())
                                        .setIcon(parameters.get("icon").asText())
                                        .setIconColor(parameters.get("iconColor").asText()));
                            if (variable.getMin() != null) {
                              builder.setMin(variable.getMin().intValue());
                            }
                            if (variable.getMax() != null) {
                              builder.setMax(variable.getMax().intValue());
                            }
                          }),
              dialogEditor -> {
                dialogEditor.disableKeepOnUi();
                dialogEditor.appearance(
                    new Icon(variable.getIcon(), variable.getIconColor()), null);
                List<ActionInputParameter> inputs =
                    List.of(
                        select("tab", context.widget().getDashboardTabs()),
                        ActionInputParameter.icon("icon", variable.getIcon()),
                        ActionInputParameter.color("iconColor", variable.getIconColor()),
                        textRequired("name", variable.getTitle(), 3, 20));
                dialogEditor.submitButton("Create Widget", button -> {}).group("General", inputs);
              });
      return null;
    }

    @UIContextMenuAction(
        value = "TITLE.CREATE_GAUGE_WIDGET",
        icon = "fas fa-gauge",
        iconColor = "#2DA3A4")
    public ActionResponseModel createGaugeWidget(Context context) {
      context
          .ui()
          .dialog()
          .sendDialogRequest(
              "create-gauge-widget",
              "TITLE.CREATE_GAUGE_WIDGET",
              (responseType, pressedButton, parameters) -> {
                if (responseType != ContextUI.DialogResponseType.Accepted) {
                  return;
                }
                context
                    .widget()
                    .createGaugeWidget(
                        variable.getEntityID(),
                        builder -> {
                          builder
                              .setValueDataSource(variable.getFullEntityID())
                              .setGaugeForegroundColor(variable.getIconColor())
                              .attachToTab(parameters.get("tab").asText());
                          builder.addSeriesGaugeValue(
                              "Gauge value",
                              series ->
                                  series
                                      .setVerticalValuePosition(-5)
                                      .setHorizontalValuePosition(3)
                                      .setValueFontSize(0.9)
                                      .setValueSuffix(variable.getUnit()));
                          builder.addSeriesCustomValue(
                              "Gauge icon",
                              series ->
                                  series
                                      .setVerticalValuePosition(40)
                                      .setIcon(parameters.get("icon").asText())
                                      .setIconColor(parameters.get("iconColor").asText()));
                          if (variable.getMin() != null) {
                            builder.setMin(variable.getMin().intValue());
                          }
                          if (variable.getMax() != null) {
                            builder.setMax(variable.getMax().intValue());
                          }
                        });
              },
              dialogEditor -> {
                dialogEditor.disableKeepOnUi();
                dialogEditor.appearance(
                    new Icon(variable.getIcon(), variable.getIconColor()), null);
                List<ActionInputParameter> inputs =
                    List.of(
                        select("tab", context.widget().getDashboardTabs()),
                        ActionInputParameter.icon("icon", variable.getIcon()),
                        ActionInputParameter.color("iconColor", variable.getIconColor()));
                dialogEditor.submitButton("Create Widget", button -> {}).group("General", inputs);
              });
      return null;
    }
  }

  public static class VariableGroupSelection implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
      return OptionModel.entityList(
          parameters.context().db().findAll(WorkspaceGroup.class).stream()
              .filter(group -> !BROADCAST_GROUP.equals(group.getEntityID()))
              .collect(Collectors.toList()),
          parameters.context());
    }
  }

  public static class SubGroupSelection implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
      WorkspaceGroup group = (WorkspaceGroup) parameters.getBaseEntity();
      return OptionModel.entityList(group.getChildrenGroups(), null, parameters.context());
    }
  }
}
