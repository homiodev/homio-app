package org.touchhome.app.manager.common.v1.layout;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.manager.common.v1.UIBaseLayoutBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.field.action.v1.UIEntityBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class UIFlexLayoutBuilderImpl extends UIBaseLayoutBuilderImpl implements UIFlexLayoutBuilder,
        UIInputEntity {

    private final String entityID;
    private final String itemType = UIItemType.Flex.name();
    private final int order;

    private String title;
    private String titleColor; // for future

    @Override
    public UIInputEntity buildEntity() {
        return this;
    }

    // for serialization!
    public List<UIInputEntity> getChildren() {
        return getUiEntityBuilders(false).stream().map(UIEntityBuilder::buildEntity)
                .sorted(Comparator.comparingInt(UIInputEntity::getOrder)).collect(Collectors.toList());
    }

    @Override
    public UIFlexLayoutBuilder setTitle(String title) {
        this.title = title;
        return this;
    }
}
