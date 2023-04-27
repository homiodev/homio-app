package org.homio.app.model.entity.widget.impl.fm;

import javax.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldColorPicker;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.UIFieldSlider;
import org.homio.bundle.api.ui.field.UIFieldTableLayout;
import org.homio.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;

@Entity
public class WidgetFMEntity extends WidgetBaseEntityAndSeries<WidgetFMEntity, WidgetFMSeriesEntity>
    implements HasDynamicContextMenuActions, HasLayout, HasSourceServerUpdates {

    public static final String PREFIX = "wgtfm_";

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Media;
    }

    @Override
    public String getImage() {
        return "fas fa-folder-tree";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        setBh(3);
        setBw(3);
        super.beforePersist();
    }

    @UIField(order = 24, isRevert = true)
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
    @UIFieldGroup("FILE_NAME")
    public boolean getShowFileName() {
        return getJsonData("sfn", true);
    }

    public WidgetFMEntity setShowFileName(boolean value) {
        setJsonData("sfn", value);
        return this;
    }

    @UIField(order = 51)
    @UIFieldSlider(min = 0, max = 1, step = 0.1)
    @UIFieldGroup("FILE_NAME")
    public double getFileNameOpacity() {
        return getJsonData("fnop", 0.2);
    }

    public WidgetFMEntity setFileNameOpacity(double value) {
        setJsonData("fnop", value);
        return this;
    }

    @UIField(order = 54, isRevert = true)
    @UIFieldColorPicker
    @UIFieldGroup("FILE_NAME")
    @UIFieldReadDefaultValue
    public String getFileNameColor() {
        return getJsonData("fnc", "#ADB5BD");
    }

    public WidgetFMEntity setFileNameColor(String value) {
        setJsonData("fnc", value);
        return this;
    }

    @UIField(order = 56, showInContextMenu = true, icon = "fas fa-eye")
    @UIFieldGroup("FILE_NAME")
    public boolean getShowFileCount() {
        return getJsonData("sfc", true);
    }

    public WidgetFMEntity setShowFileCount(boolean value) {
        setJsonData("sfc", value);
        return this;
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        uiInputBuilder.addTableLayoutButton(
            "field.layout",
            8,
            8,
            this.getLayout(),
            null,
            null,
            (entityContext, params) -> {
                this.setLayout(params.getString("value"));
                entityContext.save(this);
                return ActionResponseModel.showSuccess("SUCCESS");
            },
            0);
    }
}
