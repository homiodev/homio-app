package org.homio.addon.ibkr;

import jakarta.persistence.Entity;
import org.homio.api.Context;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.UpdatableValue;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.SecureString;
import org.homio.api.widget.CustomWidgetConfigurableEntity;
import org.homio.api.widget.CustomWidgetDataStore;
import org.homio.app.model.entity.widget.impl.extra.WidgetCustomEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Set;

@Entity
@CreateSingleEntity
@UISidebarChildren(icon = "fas fa-square-rss", color = "#B33F30", allowCreateItem = false)
public class IbkrEntity extends MiscEntity implements EntityService<IbkrService>,
  HasStatusAndMsg, CustomWidgetConfigurableEntity {

  @Override
  protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    if (getUser().isEmpty()) {
      fields.add("user");
    }
    if (getPassword().isEmpty()) {
      fields.add("password");
    }
    if (!context().media().isWebDriverAvailable()) {
      fields.add("webDriver");
    }
  }

  @UIField(order = 1, inlineEditWhenEmpty = true)
  @UIFieldGroup(order = 15, value = "SECURITY", borderColor = "#23ADAB")
  public String getUser() {
    return getJsonData("user", "");
  }

  public void setUser(String value) {
    setJsonData("user", value);
  }

  @UIField(order = 1, disableEdit = true)
  @UIFieldGroup(order = 50, value = "INFO", borderColor = "#9C27B0")
  public String getAccountId() {
    return getJsonData("aid");
  }

  @UIField(order = 2)
  @UIFieldGroup("SECURITY")
  public SecureString getPassword() {
    return getJsonSecure("pwd");
  }

  public void setPassword(String value) {
    setJsonDataSecure("pwd", value);
  }

  @UIField(order = 1, inlineEdit = true)
  @UIFieldGroup("GENERAL")
  public boolean isStart() {
    return getJsonData("start", false);
  }

  public void setStart(boolean start) {
    setJsonData("start", start);
  }

  @UIField(order = 25)
  @UIFieldPort(min = 1025)
  @UIFieldGroup("GENERAL")
  public int getPort() {
    return getJsonData("port", 5000);
  }

  public void setPort(int value) {
    setJsonData("port", value);
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return "ibkr";
  }

  @Override
  public @Nullable String getDefaultName() {
    return "IBKR";
  }

  @Override
  public long getEntityServiceHashCode() {
    return getJsonDataHashCode("port");
  }

  @Override
  public @NotNull Class<IbkrService> getEntityServiceItemClass() {
    return IbkrService.class;
  }

  @Override
  public @Nullable IbkrService createService(@NotNull Context context) {
    return new IbkrService(context, this);
  }

  @Override
  public boolean isDisableDelete() {
    return true;
  }

  @UIContextMenuAction(value = "CREATE_IBKR_WIDGET",
    icon = "fas fa-table-list",
    iconColor = "#91293E")
  public ActionResponseModel createWidget() {
    // context().widget().
    return ActionResponseModel.success();
  }

  public String getUrl(String path) {
    return "http://localhost:" + getPort() + "/v1/api/" + path;
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {
    uiInputBuilder.addSelectableButton("VIEW_AS_TABLE", new Icon("fas fa-table"), (context, params) ->
      updateWidgetView(context, params, View.table));

    uiInputBuilder.addSelectableButton("VIEW_AS_BLOCKS", new Icon("fas fa-border-none"), (context, params) ->
      updateWidgetView(context, params, View.block));
  }

  private ActionResponseModel updateWidgetView(Context context, JSONObject params, View view) {
    String widgetEntityID = params.getString("entityID");
    WidgetCustomEntity widget = context.db().getRequire(widgetEntityID);
    String currentView = widget.getJsonData("view", View.table.name());
    if (!view.name().equals(currentView)) {
      widget.setJsonData("view", view);
      context.db().save(widget);
    }
    return null;
  }

  @Override
  public void assembleUIFields(@NotNull HasDynamicUIFields.UIFieldBuilder uiFieldBuilder, @NotNull HasJsonData sourceEntity) {
    UpdatableValue<String> sort = UpdatableValue.wrap(sourceEntity, Sort.positions.name(), "defaultSort");
    uiFieldBuilder.addSelect(1, sort, OptionModel.enumList(Sort.class));

    UpdatableValue<Boolean> showSummary = UpdatableValue.wrap(sourceEntity, false, "showSummary");
    uiFieldBuilder.addSwitch(2, showSummary);

    UpdatableValue<Boolean> showTrades = UpdatableValue.wrap(sourceEntity, false, "showTrades");
    uiFieldBuilder.addSwitch(3, showTrades);

    UpdatableValue<String> tableBackground = UpdatableValue.wrap(sourceEntity, "#424242", "tableBackground");
    uiFieldBuilder.addColorPicker(4, tableBackground);

    UpdatableValue<String> tableBackgroundOdd = UpdatableValue.wrap(sourceEntity, "#5c5c5c", "tableBackgroundOdd");
    uiFieldBuilder.addColorPicker(5, tableBackgroundOdd);

    UpdatableValue<String> view = UpdatableValue.wrap(sourceEntity, View.table.name(), "view");
    uiFieldBuilder.addSelect(10, view, OptionModel.enumList(View.class));
  }

  @Override
  public void setWidgetDataStore(CustomWidgetDataStore customWidgetDataStore) {
    getService().setWidgetDataStore(customWidgetDataStore);

  }

  public enum Sort {
    price, positions
  }

  public enum View {
    table, block
  }
}
