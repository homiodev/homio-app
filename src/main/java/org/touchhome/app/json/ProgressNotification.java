package org.touchhome.app.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.ui.BaseNotificationModel;

@Getter
@Setter
@Accessors(chain = true)
public class ProgressNotification extends BaseNotificationModel<ProgressNotification> {

    private boolean cancellable;

    public ProgressNotification(String key, double progress, String message, boolean cancellable) {
        super(key);
        this.setValue(progress);
        this.setTitle(message);
        this.cancellable = cancellable;
    }
}
