package org.touchhome.app.model.entity.widget;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.model.HasPosition;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@UISidebarMenu(icon = "fas fa-tachometer-alt", bg = "#107d6b", overridePath = "widgets")
@Accessors(chain = true)
@NoArgsConstructor
public abstract class WidgetBaseEntity<T extends WidgetBaseEntity> extends BaseEntity<T>
        implements HasPosition<WidgetBaseEntity>, HasJsonData<T> {

    /**
     * Uses for grouping widget by type on UI
     */
    public WidgetGroup getGroup() {
        return null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetTabEntity widgetTabEntity;

    @Getter
    private int xb = 0;

    @Getter
    private int yb = 0;

    @Getter
    private int bw = 1;

    @Getter
    private int bh = 1;

    @Lob
    @Column(length = 1048576)
    @Convert(converter = JSONObjectConverter.class)
    private JSONObject jsonData = new JSONObject();

    @UIField(order = 50)
    public abstract String getLayout();

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setLayout(getDefaultLayout());
    }

    protected String getDefaultLayout() {
        return "";
    }

    public void setLayout(String value) {
        setJsonData("layout", value);
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

    public abstract String getImage();

    public boolean updateRelations(EntityContext entityContext) {
        return false;
    }

    @UIField(order = 1)
    @UIFieldGroup("Update")
    public UpdateInterval getReloadDataInterval() {
        return getJsonDataEnum("rdi", UpdateInterval.Never);
    }

    public T setReloadDataInterval(UpdateInterval value) {
        setJsonData("rdi", value);
        return (T) this;
    }

    @UIField(order = 2)
    @UIFieldGroup("Update")
    public Boolean getListenSourceUpdates() {
        return getJsonData("lsu", Boolean.TRUE);
    }

    public T setListenSourceUpdates(Boolean value) {
        setJsonData("lsu", value);
        return (T) this;
    }

    @UIField(order = 10)
    @UIFieldGroup("Update")
    public Boolean getShowLastUpdateTimer() {
        return getJsonData("slut", Boolean.FALSE);
    }

    public T setShowLastUpdateTimer(Boolean value) {
        setJsonData("slut", value);
        return (T) this;
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Period", order = 100)
    public TimePeriod getTimePeriod() {
        return getJsonDataEnum("tp", TimePeriod.All);
    }

    public T setTimePeriod(TimePeriod value) {
        setJsonData("tp", value);
        return (T) this;
    }

    @UIField(order = 2, showInContextMenu = true)
    @UIFieldGroup("Period")
    public Boolean getShowTimeButtons() {
        return getJsonData("stb", Boolean.FALSE);
    }

    public T setShowTimeButtons(Boolean value) {
        setJsonData("stb", value);
        return (T) this;
    }

    @UIField(order = 21)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }
}
