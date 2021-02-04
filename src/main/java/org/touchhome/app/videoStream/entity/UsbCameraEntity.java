package org.touchhome.app.videoStream.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.videoStream.handler.UsbCameraHandler;
import org.touchhome.app.videoStream.ui.RestartHandlerOnChange;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class UsbCameraEntity extends BaseFFmpegStreamEntity<UsbCameraEntity> {

    public static final String PREFIX = "usbcam_";

    @Override
    @UIField(order = 5, label = "usb")
    @RestartHandlerOnChange
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    public UsbCameraHandler createCameraHandler(EntityContext entityContext) {
        return new UsbCameraHandler(this, entityContext);
    }

    @Override
    public String toString() {
        return "usb" + getTitle();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        // set 10 frames by default.
        setSnapshotOptions("-an -vsync vfr -q:v 2 -update 1 -frames:v 10");
    }
}
