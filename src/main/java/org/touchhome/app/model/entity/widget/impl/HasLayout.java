package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.bundle.api.entity.HasJsonData;

public interface HasLayout extends HasJsonData {

    String getLayout();

    default void setLayout(String value) {
        setJsonData("layout", value);
    }
}
