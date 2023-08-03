package org.homio.app.model.rest;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Accessors(chain = true)
public class EntityUIMetaData implements Comparable<EntityUIMetaData> {

    private String label;

    private Boolean hideInEdit;
    private Boolean disableEdit;
    private Boolean hideInView;

    private Boolean hideOnEmpty;
    private Object defaultValue;
    private String entityName;
    private String type;
    private String typeMetaData;
    private Boolean showInContextMenu;
    private int order;
    private String navLink;
    private Boolean required;
    private Boolean semiRequired;
    private Boolean inlineEdit;
    private Boolean copyButton;
    private Boolean inlineEditWhenEmpty;
    private String color;
    private String icon;
    private String style;

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
        return "EntityUIMetaData{" + "entityName='" + entityName + '\'' + '}';
    }
}
