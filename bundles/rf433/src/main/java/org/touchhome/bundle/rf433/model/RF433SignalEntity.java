package org.touchhome.bundle.rf433.model;

import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
// TODO: what is this???
public class RF433SignalEntity extends RF433DeviceEntity {

    @UIField(order = 10, readOnly = true)
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getShortTitle() {
        return "Rf433Sig";
    }
}
