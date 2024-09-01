package org.homio.app.model.entity.widget.impl.toggle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.ContextWidget.ToggleType;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Entity
public class WidgetSimpleToggleEntity extends WidgetEntity<WidgetSimpleToggleEntity>
        implements HasBackground, HasSourceServerUpdates, HasSingleValueDataSource, HasToggle, HasAlign, HasPadding {

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public @NotNull String getImage() {
        return "";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-toggle";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }

    // @UIField(order = 32)
    public ToggleType getDisplayType() {
        return getJsonDataEnum("displayType", ToggleType.Slide);
    }

    public void setDisplayType(ToggleType value) {
        setJsonData("displayType", value);
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowLastUpdateTimer() {
        throw new NotImplementedException();
    }

    @Override
    public void beforePersist() {
        if (!getJsonData().has("color")) {
            setColor(UI.Color.random());
        }
        if (getOnValues().isEmpty()) {
            setOnValues("true%s1".formatted(LIST_DELIMITER));
        }
    }
}
