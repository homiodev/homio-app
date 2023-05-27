package org.homio.app.model.entity.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.OptionModel;
import org.springframework.data.util.Pair;

public final class SeriesBuilder {

    public static ChartOptionsBuilder seriesOptions() {
        return new ChartOptionsBuilder();
    }

    private static <T extends BaseEntity> List<OptionModel> buildOptions(
        EntityContext entityContext, Class<T> type, String prefix) {
        return entityContext.findAll(type).stream()
                            .map(e -> OptionModel.of(e.getEntityID(), prefix + e.getTitle()))
                            .collect(Collectors.toList());
    }

    public static class ChartOptionsBuilder {

        private final List<Pair<Class, String>> options = new ArrayList<>(4);

        public ChartOptionsBuilder add(Class<? extends BaseEntity> entityClass, String prefix) {
            options.add(Pair.of(entityClass, prefix));
            return this;
        }

        public ChartOptionsBuilder add(Class entityClass) {
            options.add(Pair.of(entityClass, ""));
            return this;
        }

        public List<OptionModel> build(EntityContext entityContext) {
            List<OptionModel> list = new ArrayList<>();
            for (Pair<Class, String> pair : options) {
                list.addAll(
                    SeriesBuilder.buildOptions(
                        entityContext, pair.getFirst(), pair.getSecond()));
            }
            return list;
        }
    }
}
