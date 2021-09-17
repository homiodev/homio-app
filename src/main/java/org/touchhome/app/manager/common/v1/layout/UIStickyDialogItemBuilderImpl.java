package org.touchhome.app.manager.common.v1.layout;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.manager.common.v1.UIBaseLayoutBuilderImpl;
import org.touchhome.app.manager.common.v1.UIDialogInputEntity;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.field.action.v1.UIEntityBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class UIStickyDialogItemBuilderImpl extends UIBaseLayoutBuilderImpl implements UIStickyDialogItemBuilder,
        UIInputEntity {

    private final String entityID;
    private final String itemType = UIItemType.StickDialog.name();
    private String background;

    @Override
    public UIStickyDialogItemBuilder setBackgroundColor(String backgroundColor) {
        this.background = backgroundColor;
        return this;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    public UIInputEntity buildEntity() {
        List<UIInputEntity> entities = getUiEntityBuilders(false).stream().map(UIEntityBuilder::buildEntity)
                .sorted(Comparator.comparingInt(UIInputEntity::getOrder)).collect(Collectors.toList());
        return new UIDialogInputEntity(entityID, 0, itemType, getTitle(), null, getStyle(), null, entities);
    }
}
