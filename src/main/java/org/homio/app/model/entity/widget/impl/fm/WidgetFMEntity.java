package org.homio.app.model.entity.widget.impl.fm;

import jakarta.persistence.Entity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTableLayout;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
@Entity
public class WidgetFMEntity extends WidgetBaseEntityAndSeries<WidgetFMEntity, WidgetFMSeriesEntity>
        implements HasDynamicContextMenuActions, HasLayout, HasSourceServerUpdates {

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "fm";
    }

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Media;
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-folder-tree";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 24)
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getBorderColor() {
        return getJsonData("bordC", "#434B5A");
    }

    public WidgetFMEntity setBorderColor(String value) {
        setJsonData("bordC", value);
        return this;
    }

    @UIField(order = 26)
    @UIFieldSlider(min = 0, max = 10)
    public int getMargin() {
        return getJsonData("mg", 1);
    }

    public WidgetFMEntity setMargin(int value) {
        setJsonData("mg", value);
        return this;
    }

    @Override
    @UIField(order = 35, showInContextMenu = true, icon = "fas fa-table")
    @UIFieldTableLayout
    public String getLayout() {
        return getJsonData("layout", "2x2");
    }

    @UIField(order = 40, showInContextMenu = true, icon = "fas fa-clock")
    @UIFieldSlider(min = 0, max = 600)
    public int getAutoRefreshTimeout() {
        return getJsonData("art", 0);
    }

    public WidgetFMEntity setAutoRefreshTimeout(int value) {
        setJsonData("art", value);
        return this;
    }

    @UIField(order = 50, showInContextMenu = true, icon = "fas fa-eye")
    @UIFieldGroup("UI")
    public boolean getShowFileName() {
        return getJsonData("sfn", true);
    }

    public WidgetFMEntity setShowFileName(boolean value) {
        setJsonData("sfn", value);
        return this;
    }

    @UIField(order = 54)
    @UIFieldColorPicker
    @UIFieldGroup("UI")
    @UIFieldReadDefaultValue
    public String getFileColor() {
        return getJsonData("fnc", "#ADB5BDAA");
    }

    public void setFileColor(String value) {
        setJsonData("fnc", value);
    }

    @UIField(order = 55)
    @UIFieldColorPicker
    @UIFieldGroup("UI")
    @UIFieldReadDefaultValue
    public String getDirectoryColor() {
        return getJsonData("dnc", "#D8D03AAA");
    }

    public void setDirectoryColor(String value) {
        setJsonData("dnc", value);
    }

    @UIField(order = 56, showInContextMenu = true, icon = "fas fa-eye")
    @UIFieldGroup("UI")
    public boolean getShowFileCount() {
        return getJsonData("sfc", true);
    }

    public WidgetFMEntity setShowFileCount(boolean value) {
        setJsonData("sfc", value);
        return this;
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        uiInputBuilder.addTableLayoutButton("LAYOUT", 8, 8, getLayout(), null,
            (context, params) -> {
                    this.setLayout(params.getString("value"));
                context.db().save(this);
                    return ActionResponseModel.showSuccess("SUCCESS");
                },
                0);
    }

    @Override
    public void beforePersist() {
        setBh(3);
        setBw(3);
        super.beforePersist();
    }
}
