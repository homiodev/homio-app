package org.touchhome.app.model.entity.widget;

import org.springframework.data.util.Pair;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class SeriesBuilder {

    private static <T extends BaseEntity> List<Option> buildOptions(EntityContext entityContext, Class<T> type, String prefix) {
        return entityContext.findAll(type).stream()
                .map(e -> Option.of(e.getEntityID(), prefix + e.getTitle())).collect(Collectors.toList());
    }

    public static ChartOptionsBuilder seriesOptions() {
        return new ChartOptionsBuilder();
    }

    public static class ChartOptionsBuilder {

        private List<Pair<Class<? extends BaseEntity>, String>> options = new ArrayList<>(4);

        public ChartOptionsBuilder add(Class<? extends BaseEntity> entityClass, String prefix) {
            options.add(Pair.of(entityClass, prefix));
            return this;
        }

        public ChartOptionsBuilder add(Class<? extends BaseEntity> entityClass) {
            options.add(Pair.of(entityClass, ""));
            return this;
        }

        public List<Option> build(EntityContext entityContext) {
            List<Option> list = new ArrayList<>();
            for (Pair<Class<? extends BaseEntity>, String> pair : options) {
                list.addAll(SeriesBuilder.buildOptions(entityContext, pair.getFirst(), pair.getSecond()));
            }
            return list;

        }
    }
}
