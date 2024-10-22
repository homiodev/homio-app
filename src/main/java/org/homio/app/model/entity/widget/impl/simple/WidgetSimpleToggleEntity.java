package org.homio.app.model.entity.widget.impl.simple;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.ContextWidget.ToggleType;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasBackground;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.impl.toggle.HasToggle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
public class WidgetSimpleToggleEntity extends WidgetEntity<WidgetSimpleToggleEntity>
        implements HasBackground, HasSingleValueDataSource, HasToggle, HasAlign, HasPadding {

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Simple;
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-tgl";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    // @UIField(order = 32)
    public ToggleType getDisplayType() {
        return getJsonDataEnum("displayType", ToggleType.Slide);
    }

    public void setDisplayType(ToggleType value) {
        setJsonData("displayType", value);
    }

    @Override
    public void beforePersist() {
        if (!getJsonData().has("color")) {
            setToggleColor(UI.Color.random());
        }
        if (getOnValues().isEmpty()) {
            setOnValues("true%s1".formatted(LIST_DELIMITER));
        }
    }

    @Override
    @UIFieldGroup("ON_OFF")
    public String getPushToggleOffValue() {
        return HasToggle.super.getPushToggleOffValue();
    }

    @Override
    @UIFieldGroup("ON_OFF")
    public List<String> getOnValues() {
        return HasToggle.super.getOnValues();
    }

    @Override
    @UIFieldGroup("ON_OFF")
    public String getPushToggleOnValue() {
        return HasToggle.super.getPushToggleOnValue();
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getOffName() {
        return HasToggle.super.getOffName();
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getOnName() {
        return HasToggle.super.getOnName();
    }
}
