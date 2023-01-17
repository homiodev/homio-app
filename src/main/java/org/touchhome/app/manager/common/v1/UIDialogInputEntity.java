package org.touchhome.app.manager.common.v1;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;

@Getter
@AllArgsConstructor
public class UIDialogInputEntity implements UIInputEntity {

    private final String entityID;
    private final int order;
    private final String itemType;
    private final String title;
    private final String icon;
    private final String iconColor;
    private final String style;
    private final Integer width;
    private final List<UIInputEntity> children;
}
