package org.homio.app.model.entity.widget.impl.color;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.Entity;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.attributes.HasIconWithoutThreshold;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.homio.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.homio.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.homio.bundle.api.ui.UI;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldColorPicker;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldIgnore;
import org.homio.bundle.api.ui.field.UIFieldKeyValue;
import org.homio.bundle.api.ui.field.UIFieldKeyValue.KeyValueType;
import org.homio.bundle.api.ui.field.UIFieldLayout;
import org.homio.bundle.api.ui.field.UIFieldLayout.HorizontalAlign;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.UIFieldSlider;
import org.homio.bundle.api.ui.field.UIFieldType;
import org.homio.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

@Entity
public class WidgetColorEntity extends WidgetBaseEntity<WidgetColorEntity>
    implements
    HasLayout,
    HasIconWithoutThreshold,
    HasName,
    HasSourceServerUpdates,
    HasDynamicParameterFields {

    public static final String PREFIX = "wgtclr_";

    @UIField(order = 1)
    @UIFieldGroup(value = "NAME", order = 3)
    @UIFieldOptionFontSize
    public String getName() {
        return super.getName();
    }

    @Override
    @UIField(order = 3, isRevert = true)
    @UIFieldColorPicker // disable thresholding
    @UIFieldGroup("NAME")
    @UIFieldReadDefaultValue
    public String getNameColor() {
        return HasName.super.getNameColor();
    }

    @Override
    public String getImage() {
        return "fa fa-palette";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getBackground() {

        return super.getBackground();
    }

    @UIField(order = 10, isRevert = true)
    @UIFieldLayout(options = {"colors", "icon", "name", "brightness", "colorTemp", "onOff"})
    @UIFieldReadDefaultValue
    public String getLayout() {
        return getJsonData("layout", getDefaultLayout());
    }

    @UIField(order = 1)
    @UIFieldKeyValue(maxSize = 20, keyType = UIFieldType.String, valueType = UIFieldType.ColorPicker,
                     defaultKey = "0", showKey = false, defaultValue = "#FFFFFF", keyValueType = KeyValueType.array)
    @UIFieldGroup(value = "COLORS", order = 10)
    public String getColors() {
        return getJsonData("colors");
    }

    public void setColors(String value) {
        setJsonData("colors", value);
    }

    @UIField(order = 2)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup("COLORS")
    @UIEditReloadWidget
    public String getColorValueDataSource() {
        return getJsonData("clrv");
    }

    public void setColorValueDataSource(String value) {
        setJsonData("clrv", value);
        if (isEmpty(getColorSetValueDataSource())) {
            setColorSetValueDataSource(value);
        }
    }

    @UIField(order = 3)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup("COLORS")
    public String getColorSetValueDataSource() {
        return getJsonData("clrds");
    }

    public void setColorSetValueDataSource(String value) {
        setJsonData("clrds", value);
    }

    @UIField(order = 4, isRevert = true)
    @UIFieldSlider(min = 0, max = 40)
    @UIFieldGroup("COLORS")
    @UIFieldReadDefaultValue
    public int getCircleSpacing() {
        return getJsonData("space", 14);
    }

    public void setCircleSpacing(int value) {
        setJsonData("space", value);
    }

    @UIField(order = 5, isRevert = true)
    @UIFieldSlider(min = 10, max = 40)
    @UIFieldGroup("COLORS")
    @UIFieldReadDefaultValue
    public int getCircleSize() {
        return getJsonData("size", 28);
    }

    public void setCircleSize(int value) {
        setJsonData("size", value);
    }

    @UIField(order = 1)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "BRIGHTNESS", order = 20, borderColor = "#307FCF")
    @UIEditReloadWidget
    public String getBrightnessValueDataSource() {
        return getJsonData("bvds");
    }

    public void setBrightnessValueDataSource(String value) {
        setJsonData("bvds", value);
        if (isEmpty(getBrightnessSetValueDataSource())) {
            setBrightnessSetValueDataSource(value);
        }
    }

    @UIField(order = 2)
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup("BRIGHTNESS")
    public String getBrightnessSetValueDataSource() {
        return getJsonData("bsvds");
    }

    public void setBrightnessSetValueDataSource(String value) {
        setJsonData("bsvds", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("BRIGHTNESS")
    public int getBrightnessMinValue() {
        return getJsonData("brtmin", 0);
    }

    public void setBrightnessMinValue(int value) {
        setJsonData("brtmin", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("BRIGHTNESS")
    public int getBrightnessMaxValue() {
        return getJsonData("brtmax", 255);
    }

    public void setBrightnessMaxValue(int value) {
        setJsonData("brtmax", value);
    }

    @UIField(order = 5, label = "sliderColor")
    @UIFieldGroup("BRIGHTNESS")
    @UIFieldColorPicker
    public String getBrightnessSliderColor() {
        return getJsonData("brtslcol");
    }

    public void setBrightnessSliderColor(String value) {
        setJsonData("brtslcol", value);
    }

    @UIField(order = 1)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "COLOR_TEMPERATURE", order = 25, borderColor = "#29B3B1")
    @UIEditReloadWidget
    public String getColorTemperatureValueDataSource() {
        return getJsonData("clrtmp");
    }

    public void setColorTemperatureValueDataSource(String value) {
        setJsonData("clrtmp", value);
        if (isEmpty(getColorTemperatureSetValueDataSource())) {
            setColorTemperatureSetValueDataSource(value);
        }
    }

    @UIField(order = 2)
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup("COLOR_TEMPERATURE")
    public String getColorTemperatureSetValueDataSource() {
        return getJsonData("clrtmps");
    }

    public void setColorTemperatureSetValueDataSource(String value) {
        setJsonData("clrtmps", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("COLOR_TEMPERATURE")
    public int getColorTemperatureMinValue() {
        return getJsonData("clrtmpmin", 0);
    }

    public void setColorTemperatureMinValue(int value) {
        setJsonData("clrtmpmin", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("COLOR_TEMPERATURE")
    public int getColorTemperatureMaxValue() {
        return getJsonData("clrtmpmax", 255);
    }

    public void setColorTemperatureMaxValue(int value) {
        setJsonData("clrtmpmax", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "ON_OFF", order = 30, borderColor = "#009688")
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIEditReloadWidget
    public String getOnOffValueDataSource() {
        return getJsonData("onOff");
    }

    public void setOnOffValueDataSource(String value) {
        setJsonData("onOff", value);
        if (isEmpty(getOnOffSetValueDataSource())) {
            setOnOffSetValueDataSource(value);
        }
    }

    @UIField(order = 1)
    @UIFieldGroup("ON_OFF")
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    public String getOnOffSetValueDataSource() {
        return getJsonData("onOffSet");
    }

    public void setOnOffSetValueDataSource(String value) {
        setJsonData("onOffSet", value);
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        if (!getJsonData().has("colors")) {
            setColors(Stream.of("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#00FFFF", "#FFFFFF")
                            .map(color -> String.format("{\"key\":\"0\",\"value\":\"%s\"}", color))
                            .collect(Collectors.joining(",", "[", "]")));
        }
        if (!getJsonData().has("size")) {
            setCircleSize(20);
        }
        if (!getJsonData().has("space")) {
            setCircleSpacing(4);
        }
        if (!getJsonData().has("brtslcol")) {
            setBrightnessSliderColor(UI.Color.random());
        }
    }

    private String getDefaultLayout() {
        return UIFieldLayout.LayoutBuilder
            .builder(15, 20, 50, 15)
            .addRow(rb -> rb
                .addCol("icon", HorizontalAlign.center)
                .addCol("name", HorizontalAlign.left)
                .addCol("brightness", HorizontalAlign.center)
                .addCol("onOff", HorizontalAlign.center))
            .addRow(rb -> rb
                .addCol("colors", HorizontalAlign.center, 4))
            .build();
    }
}
