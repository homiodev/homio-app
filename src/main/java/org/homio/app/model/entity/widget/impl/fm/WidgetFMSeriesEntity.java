package org.homio.app.model.entity.widget.impl.fm;

import jakarta.persistence.Entity;
import java.util.List;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@Entity
public class WidgetFMSeriesEntity extends WidgetSeriesEntity<WidgetFMEntity>
        implements HasSingleValueDataSource {

    @Override
    @UIField(order = 14, required = true)
    @UIFieldTreeNodeSelection(
            allowSelectDirs = true,
            allowSelectFiles = false,
            iconColor = "#14A669")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "FILTER", order = 100, borderColor = "#C1C436")
    public boolean getShowDirectories() {
        return getJsonData("sd", false);
    }

    public void setShowDirectories(boolean value) {
        setJsonData("sd", value);
    }

    @UIField(order = 2, type = UIFieldType.Chips)
    @UIFieldGroup("FILTER")
    public List<String> getFileFilters() {
        return getJsonDataList("flt");
    }

    public void setFileFilters(String value) {
        setJsonData("flt", value);
    }

    @Override
    protected String getSeriesPrefix() {
        return "fm";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
