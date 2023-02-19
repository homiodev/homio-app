package org.touchhome.app.manager;

import static org.touchhome.app.model.entity.widget.WidgetTabEntity.GENERAL_WIDGET_TAB_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.WidgetGroup;
import org.touchhome.app.model.entity.widget.WidgetTabEntity;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;

@Log4j2
@Component
@RequiredArgsConstructor
public class WidgetService implements ContextCreated {

    private final EntityContext entityContext;
    private final List<WidgetBaseEntity<?>> widgetBaseEntities;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) {
        if (this.entityContext.getEntity(GENERAL_WIDGET_TAB_NAME) == null) {
            this.entityContext.save(
                    new WidgetTabEntity().setEntityID(GENERAL_WIDGET_TAB_NAME).setName("MainTab"));
        }
    }

    public List<AvailableWidget> getAvailableWidgets() {
        List<AvailableWidget> options = new ArrayList<>();
        AvailableWidget chartWidgets = new AvailableWidget("chart-widgets", "fas fa-chart-simple", null, new ArrayList<>());
        AvailableWidget mediaWidgets = new AvailableWidget("media-widgets", "fas fa-compact-disc", null, new ArrayList<>());
        options.add(chartWidgets);
        options.add(mediaWidgets);
        for (WidgetBaseEntity<?> entity : this.widgetBaseEntities) {
            if (entity.isVisible()) {
                AvailableWidget widget = new AvailableWidget(entity.getType(), entity.getImage(), null, null);
                if (entity.getGroup() == WidgetGroup.Chart) {
                    chartWidgets.children.add(widget);
                } else if (entity.getGroup() == WidgetGroup.Media) {
                    mediaWidgets.children.add(widget);
                } else {
                    options.add(widget);
                }
            }
        }

        AvailableWidget extraWidgets = new AvailableWidget("extra-widgets", "fas fa-cheese", null, new ArrayList<>());
        for (Map.Entry<String, Collection<WidgetBaseTemplate>> entry :
                entityContext.getBeansOfTypeByBundles(WidgetBaseTemplate.class).entrySet()) {
            AvailableWidget bundleExtraWidget = new AvailableWidget(entry.getKey(), "http", null, new ArrayList<>());
            for (WidgetBaseTemplate widgetBase : entry.getValue()) {
                bundleExtraWidget.children.add(new AvailableWidget(widgetBase.getClass().getSimpleName(), widgetBase.getIcon(), null, null));
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
        private final String icon;
        private final String color;
        private final List<AvailableWidget> children;
    }
}
