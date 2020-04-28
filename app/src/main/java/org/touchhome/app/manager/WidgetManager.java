package org.touchhome.app.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.model.entity.widget.impl.WidgetTabEntity;
import org.touchhome.bundle.api.EntityContext;

@Log4j2
@Component
@RequiredArgsConstructor
public class WidgetManager {

    private final EntityContext entityContext;

    public void postConstruct() {
        if (entityContext.getEntity(WidgetTabEntity.PREFIX + WidgetTabEntity.GENERAL_WIDGET_TAB_NAME) == null) {
            entityContext.save(new WidgetTabEntity().computeEntityID(() -> WidgetTabEntity.GENERAL_WIDGET_TAB_NAME));
        }
    }
}
