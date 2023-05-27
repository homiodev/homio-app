package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;

public interface HasLayout extends HasJsonData {

    String getLayout();

    default void setLayout(String value) {
        value = value.replaceAll("'", "\"");
        setJsonData("layout", value);
    }
}
