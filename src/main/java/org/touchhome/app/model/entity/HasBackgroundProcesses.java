package org.touchhome.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface HasBackgroundProcesses {
    @JsonIgnore
    ScriptEntity[] getAvailableBackgroundProcesses();

    @JsonIgnore
    default Set<ScriptEntity> getAvailableProcesses() {
        return Stream.of(getAvailableBackgroundProcesses()).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    default ScriptEntity getBackgroundProcessScript(String scriptDescriptor) {
        for (ScriptEntity scriptEntity : getAvailableProcesses()) {
            if (scriptDescriptor.equals(scriptEntity.getDescription())) {
                return scriptEntity;
            }
        }
        return null;
    }
}
