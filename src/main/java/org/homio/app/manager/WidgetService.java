package org.homio.app.manager;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.spring.ContextCreated;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class WidgetService implements ContextCreated {

    private final Context context;
    private final List<WidgetEntity<?>> widgetBaseEntities;

    @Override
    public void onContextCreated(ContextImpl context) {
        WidgetTabEntity.ensureMainTabExists(context);
    }

    public List<AvailableWidget> getAvailableWidgets() {
        List<AvailableWidget> options = new ArrayList<>();
        AvailableWidget chartWidgets = new AvailableWidget("chart-widgets", "fas fa-chart-simple", new ArrayList<>());
        AvailableWidget mediaWidgets = new AvailableWidget("media-widgets", "fas fa-compact-disc", new ArrayList<>());
        options.add(chartWidgets);
        options.add(mediaWidgets);
        for (WidgetEntity<?> entity : this.widgetBaseEntities) {
            if (entity.isVisible()) {
                AvailableWidget widget = new AvailableWidget(entity.getType(), entity.getImage(), null);
                if (entity.getGroup() == WidgetGroup.Chart) {
                    chartWidgets.children.add(widget);
                } else if (entity.getGroup() == WidgetGroup.Media) {
                    mediaWidgets.children.add(widget);
                } else {
                    options.add(widget);
                }
            }
        }

        AvailableWidget extraWidgets = new AvailableWidget("extra-widgets", "fas fa-cheese", new ArrayList<>());
        extraWidgets.children.add(new AvailableWidget("IBKR", "fas fa-user", null));
        options.add(extraWidgets);
        /*AvailableWidget extraWidgets = new AvailableWidget("extra-widgets", "fas fa-cheese", new ArrayList<>());
        Map<ParentWidget, AvailableWidget> widgetMap = new HashMap<>();

        for (WidgetBaseTemplate template : context.getBeansOfType(WidgetBaseTemplate.class)) {
                ParentWidget parent = template.getParent();
                widgetMap.computeIfAbsent(parent, parentWidget ->
                             new AvailableWidget(parentWidget.name(), parentWidget.getIcon(), new ArrayList<>()).setColor(parentWidget.getColor()))
                         .getChildren()
                         .add(new AvailableWidget(template.getName(), template.getIcon().getIcon(), null).setColor(template.getIcon().getColor()));
        }
        extraWidgets.children.addAll(widgetMap.values());
        if (!extraWidgets.children.isEmpty()) {
            options.add(extraWidgets);
        }*/
        return options;
    }

    @Getter
    @RequiredArgsConstructor
    public static class AvailableWidget {

        private @NotNull final String key;
        private final String icon;
        private final List<AvailableWidget> children;
        private @Setter @Accessors(chain = true) String color;
    }
}
