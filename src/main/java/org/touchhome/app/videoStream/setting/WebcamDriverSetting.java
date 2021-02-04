package org.touchhome.app.videoStream.setting;

import com.github.sarxos.webcam.WebcamCompositeDriver;
import com.github.sarxos.webcam.WebcamDriver;
import com.github.sarxos.webcam.ds.dummy.WebcamDummyDriver;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptions;

import java.util.Collection;
import java.util.stream.Collectors;

public class WebcamDriverSetting implements SettingPluginOptions<WebcamDriver> {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Class<WebcamDriver> getType() {
        return WebcamDriver.class;
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public Collection<OptionModel> getOptions(EntityContext entityContext) {
        return entityContext.getClassesWithParent(WebcamDriver.class, "com.github.sarxos.webcam").stream()
                .filter(c -> !c.isAssignableFrom(WebcamCompositeDriver.class) && !c.isAssignableFrom(WebcamDummyDriver.class))
                .map(c -> OptionModel.of(c.getName(), c.getSimpleName())).collect(Collectors.toList());
    }
}
