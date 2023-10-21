package org.homio.app.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.widget.WidgetBaseTemplate;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.spring.ContextCreated;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class WidgetService implements ContextCreated {

    private final Context context;
    private final List<WidgetBaseEntity<?>> widgetBaseEntities;

    @Override
    public void onContextCreated(ContextImpl context) {
        WidgetTabEntity.ensureMainTabExists(context);
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
            context.getBeansOfTypeByAddons(WidgetBaseTemplate.class).entrySet()) {
            AvailableWidget availableWidget = new AvailableWidget(entry.getKey(), "http", null, new ArrayList<>());
            for (WidgetBaseTemplate widgetBase : entry.getValue()) {
                availableWidget.children.add(new AvailableWidget(widgetBase.getClass().getSimpleName(), widgetBase.getIcon(), null, null));
            }
            if (!availableWidget.children.isEmpty()) {
                extraWidgets.children.add(availableWidget);
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
