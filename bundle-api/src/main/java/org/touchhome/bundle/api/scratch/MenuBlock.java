package org.touchhome.bundle.api.scratch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
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
        return new StaticMenuBlock(name, null).add(enumClass);
    }

    public static StaticMenuBlock ofStaticList(String name, String... items) {
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
        @AllArgsConstructor
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
        private List items = new ArrayList<>();
        private Map<String, List> subMenu;

        StaticMenuBlock(String name, String[] list) {
            super(name);
            if (list != null) {
                Collections.addAll(this.items, list);
            }
        }

        public StaticMenuBlock add(String key, Object value) {
            this.items.add(new StaticMenuItem(key, value.toString()));
            return this;
        }

        public StaticMenuBlock add(Class<? extends Enum> enumClass) {
            for (Enum item : enumClass.getEnumConstants()) {
                this.items.add(new StaticMenuItem(item.name(), item.toString()));
            }
            return this;
        }

        public <T extends Enum, S extends Enum> void subMenu(T key, Class<S> subMenu) {
            if (this.subMenu == null) {
                this.subMenu = new HashMap<>();
            }
            this.subMenu.put(key.name(), Stream.of(subMenu.getEnumConstants()).map(Enum::name).collect(Collectors.toList()));

        }

        @Getter
        @AllArgsConstructor
        private static class StaticMenuItem {
            private String text;
            private String value;
        }
    }
}
