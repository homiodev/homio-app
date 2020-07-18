package org.touchhome.bundle.api.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Getter
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Option implements Comparable<Option> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String key;
    private String entityID;
    private String title;

    @Setter
    private String imageRef;

    @Setter
    private String json;

    public Option(Object key, Object title) {
        this.key = String.valueOf(key);
        this.entityID = this.key;
        this.title = String.valueOf(title);
    }

    public static Option key(String key) {
        Option option = new Option();
        option.key = key;
        option.entityID = key;
        option.title = key;
        return option;
    }

    public static Option of(String key, String title) {
        return new Option(key, title);
    }

    public static List<Option> listWithEmpty(Class<? extends Enum> enumClass) {
        List<Option> list = list(enumClass);
        list.add(Option.key(""));
        return list;
    }

    public static List<Option> list(Class<? extends Enum> enumClass) {
        return Stream.of(enumClass.getEnumConstants()).map(n -> Option.key(n.name())).collect(Collectors.toList());
    }

    public static List<Option> list(Option... options) {
        return Stream.of(options).collect(Collectors.toList());
    }

    public static List<Option> range(int min, int max) {
        return IntStream.range(min, max).mapToObj(value -> Option.key(String.valueOf(value))).collect(Collectors.toList());
    }

    public static <T extends BaseEntity> List<Option> list(List<T> list) {
        return list.stream().map(e -> Option.of(e.getEntityID(), StringUtils.defaultIfEmpty(e.getName(), e.getTitle()))).collect(Collectors.toList());
    }

    public static List<Option> simpleNamelist(Collection list) {
        List<Option> options = new ArrayList<>();
        for (Object o : list) {
            options.add(Option.key(o.getClass().getSimpleName()));
        }
        return options;
    }

    @Override
    @SneakyThrows
    public String toString() {
        return OBJECT_MAPPER.writeValueAsString(this);
    }

    @Override
    public int compareTo(@NotNull Option other) {
        return this.title.compareTo(other.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Option)) {
            return false;
        }
        Option option = (Option) o;
        return Objects.equals(key, option.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @SneakyThrows
    public Option addJson(String key, String value) {
        Map<String, Object> jsonObject;
        if (json == null) {
            jsonObject = new HashMap<>();
        } else {
            jsonObject = OBJECT_MAPPER.readValue(json, HashMap.class);
        }
        jsonObject.put(key, value);
        json = OBJECT_MAPPER.writeValueAsString(jsonObject);
        return this;
    }
}
