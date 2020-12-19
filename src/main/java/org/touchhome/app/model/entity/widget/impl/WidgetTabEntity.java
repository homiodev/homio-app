package org.touchhome.app.model.entity.widget.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.entity.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetTabEntity extends BaseEntity<WidgetTabEntity> implements Comparable<WidgetTabEntity> {
    public static final String PREFIX = "wt_";
    public static final String GENERAL_WIDGET_TAB_NAME = "main";

    @Getter
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "widgetTabEntity")
    private Set<WidgetBaseEntity> widgetBaseEntities;

    @Override
    public int compareTo(@NotNull WidgetTabEntity o) {
        return this.getCreationTime().compareTo(o.getCreationTime());
    }

    @Override
    protected void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 10) {
            throw new IllegalStateException("Widget tab name must be between 2..10 characters");
        }
    }

    @Override
    protected void beforeRemove() {
        if (this.getName().equals(GENERAL_WIDGET_TAB_NAME)) {
            throw new IllegalStateException("Unable to delete default tab");
        }
        if (!widgetBaseEntities.isEmpty()) {
            throw new IllegalStateException("Unable to delete non empty tab");
        }
    }
}
