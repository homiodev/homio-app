package org.touchhome.bundle.api.scratch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public class MenuBlock {
    @JsonIgnore
    private final String name;

    public static ServerMenuBlock ofServer(String name, String url, String firstKey, String firstValue, Integer... clusters) {
        return new ServerMenuBlock(name, url, firstKey, firstValue, clusters);
    }

    public static ServerMenuBlock ofServer(String name, String url, String firstKey, String firstValue) {
        return new ServerMenuBlock(name, url, firstKey, firstValue, null);
    }

    public static StaticMenuBlock ofStatic(String name, Class<? extends Enum> enumClass) {
        return new StaticMenuBlock(name, null).addEnum(enumClass);
    }

    public static <T extends Enum> StaticMenuBlock ofStatic(String name, Class<T> enumClass, Predicate<T> filter) {
        return new StaticMenuBlock(name, null).addEnum(enumClass, filter);
    }

    public static StaticMenuBlock ofStaticList(String name, Map<String, String> items) {
        return new StaticMenuBlock(name, items);
    }

    @Getter
    public static class ServerMenuBlock extends MenuBlock {
        private final boolean acceptReporters = true;
        private final boolean async = true;
        private final MenuBlockFunction items;
        @JsonIgnore
        private final Integer[] clusters;

        ServerMenuBlock(String name, String url, String keyName, String valueName, String firstKey, String firstValue, Integer[] clusters) {
            super(name);
            this.clusters = clusters;
            this.items = new MenuBlockFunction(url, keyName, valueName, new String[]{firstKey, firstValue});
        }

        ServerMenuBlock(String name, String url, String firstKey, String firstValue, Integer[] clusters) {
            this(name, url, null, null, firstKey, firstValue, clusters);
        }

        @Getter
        @RequiredArgsConstructor
        static class MenuBlockFunction {
            private final String url;
            private final String keyName;
            private final String valueName;
            private final String[] firstKV;
        }
    }

    @Getter
    public static class StaticMenuBlock extends MenuBlock {
        private boolean acceptReporters = true;
        private List<StaticMenuItem> items = new ArrayList<>();
        private Map<String, List> subMenu;

        StaticMenuBlock(String name, Map<String, String> map) {
            super(name);
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    this.items.add(new StaticMenuItem(entry.getKey(), entry.getValue()));
                }
            }
        }

        public StaticMenuBlock add(String key, Object value) {
            this.items.add(new StaticMenuItem(key, value.toString()));
            return this;
        }

        public StaticMenuBlock addEnum(Class<? extends Enum> enumClass) {
            for (Enum item : enumClass.getEnumConstants()) {
                this.items.add(new StaticMenuItem(item.name(), item.toString()));
            }
            return this;
        }

        public <T extends Enum> StaticMenuBlock addEnum(Class<T> enumClass, Predicate<T> filter) {
            for (T item : enumClass.getEnumConstants()) {
                if (filter.test(item)) {
                    this.items.add(new StaticMenuItem(item.name(), item.toString()));
                }
            }
            return this;
        }

        public <T extends Enum, S extends Enum> void subMenu(T key, Class<S> subMenu) {
            if (this.subMenu == null) {
                this.subMenu = new HashMap<>();
            }
            this.subMenu.put(key.name(), Stream.of(subMenu.getEnumConstants()).map(Enum::name).collect(Collectors.toList()));

        }

        public String getFirstValue() {
            return this.items.isEmpty() ? null : this.items.get(0).getText();
        }

        @Getter
        @AllArgsConstructor
        private static class StaticMenuItem {
            private String text;
            private String value;
        }
    }
}
