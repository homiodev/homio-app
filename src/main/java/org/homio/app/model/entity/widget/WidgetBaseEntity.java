package org.homio.app.model.entity.widget;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.JSON;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.app.model.entity.widget.attributes.HasPosition;
import org.homio.app.model.entity.widget.attributes.HasStyle;
import org.homio.app.setting.dashboard.DashboardHorizontalBlockCountSetting;
import org.homio.app.setting.dashboard.DashboardVerticalBlockCountSetting;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@UISidebarMenu(icon = "fas fa-tachometer-alt", bg = "#107d6b", overridePath = "widgets")
@Accessors(chain = true)
@NoArgsConstructor
public abstract class WidgetBaseEntity<T extends WidgetBaseEntity> extends BaseEntity<T>
    implements HasPosition<WidgetBaseEntity>, HasStyle, HasJsonData {

    private static final String PREFIX = "widget_";

    @Override
    public final @NotNull String getEntityPrefix() {
        return PREFIX + getWidgetPrefix() + "_";
    }

    protected abstract @NotNull String getWidgetPrefix();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private WidgetTabEntity widgetTabEntity;

    @Column(length = 65535)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    /**
     * Uses for grouping widget by type on UI
     */
    public WidgetGroup getGroup() {
        return null;
    }

    @UIField(order = 1000)
    @UIFieldGroup(value = "UI", order = 10, borderColor = "#009688")
    public boolean isAdjustFontSize() {
        return getJsonData("adjfs", Boolean.FALSE);
    }

    public void setAdjustFontSize(boolean value) {
        setJsonData("adjfs", value);
    }

    public String getFieldFetchType() {
        return getJsonData("fieldFetchType", (String) null);
    }

    public T setFieldFetchType(String value) {
        jsonData.put("fieldFetchType", value);
        return (T) this;
    }

    @Override
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }

    public abstract @NotNull String getImage();

    /*protected boolean invalidateWrongEntity(EntityContext entityContext, Object item) {
        boolean updated = false;
        if (item instanceof HasSingleValueDataSource) {
            HasSingleValueDataSource source = (HasSingleValueDataSource) item;
            String valueDataSource = source.getValueDataSource();
            if (isNotEmpty(valueDataSource) && isEntityNotExists(entityContext, valueDataSource)) {
                updated = true;
                source.setValueDataSource(null);
            }

            if (isNotEmpty(source.getSetValueDataSource()) && isEntityNotExists(entityContext, source.getSetValueDataSource())) {
                updated = true;
                source.setSetValueDataSource(null);
            }
        }
        if (item instanceof HasChartDataSource) {
            HasChartDataSource source = (HasChartDataSource) item;
            if (isNotEmpty(source.getChartDataSource()) && isEntityNotExists(entityContext, source.getChartDataSource())) {
                updated = true;
                ((HasChartDataSource) item).setChartDataSource(null);
            }
        }
        return updated;
    }*/

  /*  private boolean isEntityNotExists(EntityContext entityContext, String source) {
        DataSourceUtil.DataSourceContext dsContext = DataSourceUtil.getSource(entityContext, source);
        return dsContext.getSource() == null;
    }*/

    @UIField(order = 21, isRevert = true)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true, pulseColorCondition = true, thresholdSource = true)
    @UIFieldReadDefaultValue
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }

    @UIField(order = 25)
    @UIFieldGroup("UI")
    @UIFieldSlider(min = 15, max = 25)
    public int getIndex() {
        return getJsonData("zi", 20);
    }

    public void setIndex(Integer value) {
        if (value == null || value == 20) {
            value = null;
        }
        setJsonData("zi", value);
    }

    public boolean isVisible() {
        return true;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        this.findSuitablePosition();
    }

    @Override
    public @NotNull String getDynamicUpdateType() {
        return "widget";
    }

    /**
     * Find free space in matrix for new item
     */
    private void findSuitablePosition() {
        List<WidgetBaseEntity> widgets = getEntityContext().findAll(WidgetBaseEntity.class);
        if (isNotEmpty(getParent())) {
            WidgetBaseEntity layout = widgets.stream().filter(w -> w.getEntityID().equals(getParent())).findAny().orElse(null);
            if (layout == null) {
                throw new IllegalArgumentException("Widget: " + getTitle() + " has xbl/tbl and have to be belong to layout widget but it's not found");
            }
            // do not change position for widget which belong to layout
            return;
        }

        var hBlockCount = getEntityContext().setting().getValue(DashboardHorizontalBlockCountSetting.class);
        var vBlockCount = getEntityContext().setting().getValue(DashboardVerticalBlockCountSetting.class);
        boolean[][] matrix = new boolean[vBlockCount][hBlockCount];
        for (int j = 0; j < vBlockCount; j++) {
            matrix[j] = new boolean[hBlockCount];
        }
        initMatrix(widgets, matrix);
        if (!isSatisfyPosition(matrix, getXb(), getYb(), getBw(), getBh(), hBlockCount, vBlockCount)) {
            Pair<Integer, Integer> freePosition = findMatrixFreePosition(matrix, getBw(), getBh(), hBlockCount, vBlockCount);
            if (freePosition == null) {
                throw new IllegalStateException("W.ERROR.NO_WIDGET_FREE_POSITION");
            }
            setXb(freePosition.getKey());
            setYb(freePosition.getValue());
        }
    }

    /**
     * Check if matrix has free slot for specific width/height and return first available position
     */
    private static Pair<Integer, Integer> findMatrixFreePosition(boolean[][] matrix, int bw, int bh, int hBlockCount, int vBlockCount) {
        for (int j = 0; j < hBlockCount; j++) {
            for (int i = 0; i < vBlockCount; i++) {
                if (isSatisfyPosition(matrix, i, j, bw, bh, hBlockCount, vBlockCount)) {
                    return Pair.of(i, j);
                }
            }
        }
        return null;
    }

    private static boolean isSatisfyPosition(boolean[][] matrix, int xPos, int yPos, int width, int height, int hBlockCount, int vBlockCount) {
        for (int j = xPos; j < xPos + width; j++) {
            for (int i = yPos; i < yPos + height; i++) {
                if (i >= vBlockCount || j >= hBlockCount || matrix[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void initMatrix(List<WidgetBaseEntity> widgets, boolean[][] matrix) {
        for (WidgetBaseEntity model : widgets) {
            if (isEmpty(model.getParent())) {
                for (int j = model.getXb(); j < model.getXb() + model.getBw(); j++) {
                    for (int i = model.getYb(); i < model.getYb() + model.getBh(); i++) {
                        matrix[i][j] = true;
                    }
                }
            }
        }
    }
}
