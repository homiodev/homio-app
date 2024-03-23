package org.homio.app.model.entity.widget.impl;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldTableLayout;
import org.homio.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.HasBackground;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.setting.dashboard.WidgetBorderColorMenuSetting;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetLayoutEntity extends WidgetEntity<WidgetLayoutEntity>
        implements HasLayout, HasBackground {

    @Override
    public @NotNull String getImage() {
        return "fas fa-layer-group";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "layout";
    }

    @UIField(order = 35, showInContextMenu = true, icon = "fas fa-table")
    @UIFieldTableLayout(maxRows = 30, maxColumns = 15)
    @UIFieldDisableEditOnCondition("return context.get('hasChildren')")
    public String getLayout() {
        return getJsonData("layout", "2x2");
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 24)
    @UIFieldGroup("UI")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getBorderColor() {
        return getJsonData("bc", context().setting().getValue(WidgetBorderColorMenuSetting.class));
    }

    public void setBorderColor(String value) {
        setJsonData("bc", value);
    }

    @Override
    public void afterDelete() {
        for (WidgetEntity entity : context().db().findAll(WidgetEntity.class)) {
            if (getEntityID().equals(entity.getParent())) {
                context().db().delete(entity);
            }
        }
    }

    public WidgetLayoutEntity() {
        setBw(2);
        setIndex(15);
    }
}
