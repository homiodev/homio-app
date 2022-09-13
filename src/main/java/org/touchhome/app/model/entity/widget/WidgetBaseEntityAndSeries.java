package org.touchhome.app.model.entity.widget;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.common.exception.ServerException;

import javax.persistence.*;
import java.util.Set;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

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
        return invalidateWrongDataSource(series, entityContext);
    }

    /**
     * Check data sources every fetch and check if relation exists. Set to null if not exists
     *
     * @return true if any data source has been invalid and set to null. This mean we need save it to database
     */
    protected boolean invalidateWrongDataSource(Set<S> series, EntityContext entityContext) {
        boolean updated = false;
        if (series != null) {
            for (S item : series) {
                if (item instanceof HasSingleValueDataSource) {
                    String valueDataSource = ((HasSingleValueDataSource) item).getValueDataSource();
                    if (isNotEmpty(valueDataSource) && entityContext.getEntity(valueDataSource) == null) {
                        updated = true;
                        ((HasSingleValueDataSource) item).setValueDataSource(null);
                    }

                    String setValueDataSource = ((HasSingleValueDataSource) item).getSetValueDataSource();
                    if (isNotEmpty(setValueDataSource) && entityContext.getEntity(setValueDataSource) == null) {
                        updated = true;
                        ((HasSingleValueDataSource) item).setValueDataSource(null);
                    }
                }
                if(item instanceof HasChartDataSource) {
                    String chartDataSource = ((HasChartDataSource) item).getChartDataSource();
                    if (isNotEmpty(chartDataSource) && entityContext.getEntity(chartDataSource) == null) {
                        updated = true;
                        ((HasChartDataSource) item).setChartDataSource(null);
                    }
                }
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
