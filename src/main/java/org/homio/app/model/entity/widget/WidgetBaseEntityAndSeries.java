package org.homio.app.model.entity.widget;

import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;
import org.homio.bundle.api.entity.BaseEntity;
import org.homio.bundle.api.entity.validation.MaxItems;
import org.homio.bundle.api.exception.ServerException;
import org.homio.bundle.api.ui.field.UIField;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class WidgetBaseEntityAndSeries<T extends WidgetBaseEntityAndSeries, S extends WidgetSeriesEntity<T>>
    extends WidgetBaseEntity<T> {

    @Getter
    @Setter
    @OrderBy("priority asc")
    @UIField(order = 30, hideInView = true)
    @MaxItems(10)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetEntity", targetEntity = WidgetSeriesEntity.class)
    private Set<S> series;

    @Override
    public void copy() {
        super.copy();
        series.forEach(BaseEntity::copy);
    }

    /* Looks like we may need verify relations only during fetch all variables from UI
    @Override
    public void afterFetch(EntityContext entityContext) {
        if (updateRelations(entityContext)) {
            entityContext.save(this);
        }
    }*/

    @Override
    protected void validate() {
        if (getWidgetTabEntity() == null) {
            throw new ServerException("Unable to save widget without attach to tab");
        }
    }
}
