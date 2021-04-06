package org.touchhome.app.model.rest;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Objects;

@Getter
@Setter
public class EntityUIMetaData implements Comparable<EntityUIMetaData> {
    private String label;
    private Boolean readOnly;
    private Boolean hideOnEmpty;
    private Boolean onlyEdit;
    private Object defaultValue;
    private String entityName;
    private String type;
    private String typeMetaData;
    private Boolean showInContextMenu;
    private int order;
    private String navLink;
    private Boolean required;
    private Boolean inlineEdit;
    private Boolean inlineEditWhenEmpty;
    private String color;

    @Override
    public int compareTo(@NotNull EntityUIMetaData entityUIMetaData) {
        return Integer.compare(order, entityUIMetaData.order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EntityUIMetaData that = (EntityUIMetaData) o;
        return Objects.equals(entityName, that.entityName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityName);
    }

    @Override
    public String toString() {
        return "EntityUIMetaData{" +
                "entityName='" + entityName + '\'' +
                '}';
    }
}
