package org.touchhome.app.model.entity.widget;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.common.exception.ServerException;

import javax.persistence.*;
import java.util.Set;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class WidgetBaseEntityAndSeries<T extends WidgetBaseEntityAndSeries, S extends WidgetSeriesEntity<T>>
        extends WidgetBaseEntity<T> {

    @Getter
    @Setter
    @OrderBy("priority asc")
    @UIField(order = 30, onlyEdit = true)
    @MaxItems(10)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetEntity",
            targetEntity = WidgetSeriesEntity.class)
    private Set<S> series;

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        boolean updated = false;
        if (series != null) {
            for (S item : series) {
                updated |= invalidateWrongEntity(entityContext, item);
            }
        }
        return updated;
    }

    @Override
    protected void validate() {
        if (getWidgetTabEntity() == null) {
            throw new ServerException("Unable to save widget without attach to tab");
        }
    }

    @Override
    public void afterFetch(EntityContext entityContext) {
        if (updateRelations(entityContext)) {
            entityContext.save(this);
        }
    }

    @Override
    public void copy() {
        super.copy();
        series.forEach(BaseEntity::copy);
    }
}
