package org.touchhome.app.model.entity.widget.impl.color;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.UIFieldLayout.HorizontalAlign;
import org.touchhome.app.model.entity.widget.UIFieldUpdateFontSize;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasLayout;
import org.touchhome.app.model.entity.widget.impl.HasName;
import org.touchhome.app.model.entity.widget.impl.HasSourceServerUpdates;
import org.touchhome.app.model.entity.widget.impl.HasStyle;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIKeyValueField;
import org.touchhome.bundle.api.ui.field.UIKeyValueField.KeyValueType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

@Entity
public class WidgetColorEntity extends WidgetBaseEntity<WidgetColorEntity>
    implements HasStyle,
    HasLayout,
    HasIcon,
    HasName,
    HasSourceServerUpdates,
    HasDynamicParameterFields {

    public static final String PREFIX = "wgtclr_";

    @UIField(order = 1)
    @UIFieldGroup(value = "Name", order = 1)
    @UIFieldUpdateFontSize
    public String getName() {
        return super.getName();
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

    @UIField(order = 10)
    @UIFieldLayout(options = {"colors", "icon", "name", "brightness", "onOff"})
    public String getLayout() {
        return getJsonData("layout");
    }

    @UIField(order = 1)
    @UIKeyValueField(maxSize = 20, keyType = UIFieldType.String, valueType = UIFieldType.ColorPicker,
                     defaultKey = "0", showKey = false, defaultValue = "#FFFFFF", keyValueType = KeyValueType.array)
    @UIFieldGroup(value = "Colors", order = 10)
    public String getColors() {
        return getJsonData("colors");
    }

    public void setColors(String value) {
        setJsonData("colors", value);
    }

    @UIField(order = 2)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup("Colors")
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
    @UIFieldGroup("Colors")
    public String getColorSetValueDataSource() {
        return getJsonData("clrds");
    }

    public void setColorSetValueDataSource(String value) {
        setJsonData("clrds", value);
    }

    @UIField(order = 4, isRevert = true)
    @UIFieldSlider(min = 0, max = 40)
    @UIFieldGroup("Colors")
    public int getCircleSpacing() {
        return getJsonData("space", 14);
    }

    public void setCircleSpacing(int value) {
        setJsonData("space", value);
    }

    @UIField(order = 5, isRevert = true)
    @UIFieldSlider(min = 10, max = 40)
    @UIFieldGroup("Colors")
    public int getCircleSize() {
        return getJsonData("size", 28);
    }

    public void setCircleSize(int value) {
        setJsonData("size", value);
    }

    @UIField(order = 1)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "Brightness", order = 20, borderColor = "#307FCF")
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
    @UIFieldGroup("Brightness")
    public String getBrightnessSetValueDataSource() {
        return getJsonData("bsvds");
    }

    public void setBrightnessSetValueDataSource(String value) {
        setJsonData("bsvds", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "OnOff", order = 30, borderColor = "#009688")
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
    @UIFieldGroup("OnOff")
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
        setColors(Stream.of("#f44336", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5"
                            , "#00bcd4", "#009688", "#4caf50", "#ffeb3b"
                            , "#ff9800", "#795548", "#607d8b")
                        .map(color -> String.format("{\"key\":\"0\",\"value\":\"%s\"}", color))
                        .collect(Collectors.joining(",", "[", "]")));
        setCircleSize(20);
        setCircleSpacing(4);

        setLayout(UIFieldLayout.LayoutBuilder
            .builder(15, 20, 50, 15)
            .addRow(rb -> rb
                .addCol("icon", UIFieldLayout.HorizontalAlign.left)
                .addCol("name", UIFieldLayout.HorizontalAlign.left)
                .addCol("brightness", HorizontalAlign.center)
                .addCol("onOff", UIFieldLayout.HorizontalAlign.right))
            .addRow(rb -> rb
                .addCol("colors", UIFieldLayout.HorizontalAlign.center, 4))
            .build());
    }
}
