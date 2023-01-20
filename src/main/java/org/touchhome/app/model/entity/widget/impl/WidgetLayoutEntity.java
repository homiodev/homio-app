package org.touchhome.app.model.entity.widget.impl;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.setting.dashboard.WidgetBorderColorMenuSetting;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldTableLayout;
import org.touchhome.bundle.api.ui.field.condition.UIFieldDisableEditOnCondition;

@Entity
public class WidgetLayoutEntity extends WidgetBaseEntity<WidgetLayoutEntity>
    implements HasLayout {

    public static final String PREFIX = "wgtcmp_";

    @Override
    public String getImage() {
        return "fas fa-layer-group";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @UIField(order = 35, showInContextMenu = true, icon = "fas fa-table")
    @UIFieldTableLayout
    @UIFieldDisableEditOnCondition("return context.get('hasChildren')")
    public String getLayout() {
        return getJsonData("layout", "2x2");
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 24, isRevert = true)
    @UIFieldGroup("UI")
    @UIFieldColorPicker
    public String getBorderColor() {
        return getJsonData("bc", getEntityContext().setting().getValue(WidgetBorderColorMenuSetting.class));
    }

    public void setBorderColor(String value) {
        setJsonData("bc", value);
    }

    @UIField(order = 31, showInContextMenu = true, icon = "fas fa-border-top-left")
    public Boolean isShowWidgetBorders() {
        return getJsonData("swb", Boolean.FALSE);
    }

    public void setShowWidgetBorders(Boolean value) {
        setJsonData("swb", value);
    }

    @Override
    public boolean isDisableDelete() {
        return isHasChildren();
    }

    public boolean isHasChildren() {
        for (WidgetBaseEntity widget : getEntityContext().findAll(WidgetBaseEntity.class)) {
            if (!widget.getEntityID().equals(this.getEntityID()) && widget.getXb() == this.getXb() && widget.getYb() == this.getYb()) {
                return true;
            }
        }
        return false;
    }
}
