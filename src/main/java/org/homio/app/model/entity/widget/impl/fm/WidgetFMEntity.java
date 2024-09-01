package org.homio.app.model.entity.widget.impl.fm;

import jakarta.persistence.Entity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
@Entity
public class WidgetFMEntity extends WidgetEntity<WidgetFMEntity>
        implements
        HasDynamicContextMenuActions,
        HasLayout,
        HasSingleValueDataSource,
        HasSourceServerUpdates {

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

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
        if (getValueDataSource().isEmpty()) {
            fields.add("valueDataSource");
        }
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

    @UIField(order = 55)
    @UIFieldGroup("UI")
    public boolean getDrawTextAsThumbnail() {
        return getJsonData("dtat", false);
    }

    public void setDrawTextAsThumbnail(boolean value) {
        setJsonData("dtat", value);
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
    @UIField(order = 14, required = true)
    @UIFieldTreeNodeSelection(
            allowSelectDirs = true,
            allowSelectFiles = false,
            iconColor = "#14A669")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "FILTER", order = 100, borderColor = "#C1C436")
    public boolean getShowDirectories() {
        return getJsonData("sd", false);
    }

    public void setShowDirectories(boolean value) {
        setJsonData("sd", value);
    }

    @UIField(order = 2, type = UIFieldType.Chips)
    @UIFieldGroup("FILTER")
    public List<String> getFileFilters() {
        return getJsonDataList("flt");
    }

    public void setFileFilters(String value) {
        setJsonData("flt", value);
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

    public WidgetFMEntity() {
        setBw(3);
        setBh(3);
    }
}
