package org.touchhome.bundle.api.jpa;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

    @Override
    public String convertToDatabaseColumn(Set<String> list) {
        return list == null ? "" : String.join(",", list);
    }

    @Override
    public LinkedHashSet<String> convertToEntityAttribute(String joined) {
        if (joined == null || joined.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(joined.split(",")));
    }
}
