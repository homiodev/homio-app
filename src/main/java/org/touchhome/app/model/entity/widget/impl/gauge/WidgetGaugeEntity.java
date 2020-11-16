package org.touchhome.app.model.entity.widget.impl.gauge;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.model.entity.widget.HasDataSource;
import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIKeyValueField;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.util.List;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetGaugeEntity extends WidgetBaseEntity<WidgetGaugeEntity> implements HasDataSource {

    @UIField(label = "widget.gauge_dataSource", order = 14, required = true)
    @UIFieldSelection(GaugeDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @UIField(order = 15)
    @Enumerated(EnumType.STRING)
    private GaugeType gaugeType = GaugeType.arch;

    @UIField(order = 16, label = "gauge.min")
    private Integer min = 0;

    @UIField(order = 17, label = "gauge.max")
    private Integer max = 255;

    @UIField(order = 18, type = UIFieldType.Slider, label = "gauge.thick")
    @UIFieldNumber(min = 1, max = 10)
    private Integer thick = 6;

    @UIField(order = 19)
    @Enumerated(EnumType.STRING)
    private LineType gaugeCapType = LineType.round;

    @UIField(order = 20, type = UIFieldType.Color)
    private String foreground = "#009688";

    @UIField(order = 21, type = UIFieldType.Color)
    private String background = "rgba(0, 0, 0, 0.1)";

    @UIField(order = 22)
    private String prepend = "";

    @UIField(order = 23)
    private String append = "";

    @UIField(order = 24)
    private Boolean animations = Boolean.FALSE;

    @UIField(order = 25)
    @UIKeyValueField(maxSize = 5, keyType = UIFieldType.Float, valueType = UIFieldType.Color, defaultKey = "0", defaultValue = "#FFFFFF")
    private String threshold = "{}";

    @Override
    public String getImage() {
        return "fas fa-tachometer-alt";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        if (dataSource != null && entityContext.getEntity(dataSource) == null) {
            this.dataSource = null;
            return true;
        }
        return false;
    }

    public enum GaugeType {
        full, semi, arch
    }

    public enum LineType {
        round, butt
    }

    public static class GaugeDataSourceDynamicOptionLoader implements DynamicOptionLoader<Void> {

        @Override
        public List<Option> loadOptions(Void parameter, BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceVariableEntity.class)
                    .build(entityContext);
        }
    }
}
