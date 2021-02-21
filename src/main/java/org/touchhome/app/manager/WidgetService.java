package org.touchhome.app.manager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.rest.WidgetController;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetTabEntity;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class WidgetService {

    private final EntityContext entityContext;
    private final List<WidgetBaseEntity<?>> widgetBaseEntities;

    public void postConstruct() {
        if (entityContext.getEntity(WidgetTabEntity.PREFIX + WidgetTabEntity.GENERAL_WIDGET_TAB_NAME) == null) {
            entityContext.save(new WidgetTabEntity().computeEntityID(() -> WidgetTabEntity.GENERAL_WIDGET_TAB_NAME));
        }
    }

    public List<AvailableWidget> getAvailableWidgets() {
        List<AvailableWidget> options = new ArrayList<>();
        for (WidgetBaseEntity<?> entity : this.widgetBaseEntities) {
            options.add(new AvailableWidget(entity.getType(), entity.getImage(), null));
        }
        AvailableWidget extraWidgets = new AvailableWidget("extra-widgets", "fas fa-cheese", new ArrayList<>());
        for (Map.Entry<String, Collection<WidgetBaseTemplate>> entry : entityContext.getBeansOfTypeByBundles(WidgetBaseTemplate.class).entrySet()) {
            AvailableWidget bundleExtraWidget = new AvailableWidget(entry.getKey(), "http", new ArrayList<>());
            for (WidgetBaseTemplate widgetBase : entry.getValue()) {
                bundleExtraWidget.children.add(new AvailableWidget(widgetBase.getClass().getSimpleName(), widgetBase.getIcon(), null));
            }
            if (!bundleExtraWidget.children.isEmpty()) {
                extraWidgets.children.add(bundleExtraWidget);
            }
        }
        if (!extraWidgets.children.isEmpty()) {
            options.add(extraWidgets);
        }
        return options;
    }

    @Getter
    @RequiredArgsConstructor
    public static class AvailableWidget {
        private final String key;
        private final String image;
        private final List<AvailableWidget> children;
    }
}
