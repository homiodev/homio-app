package org.touchhome.app.json.bgp;

import org.apache.commons.lang3.StringUtils;

public class BackgroundProcessStatusJSON {
    private String entityID;
    private String backgroundProcessDescriptor;
    private org.touchhome.bundle.api.thread.BackgroundProcessStatus status;
    private String errorMessage;
    private int progress;
    private String backgroundProcessServiceID;

    public String getEntityID() {
        return entityID;
    }

    public void setEntityID(String entityID) {
        this.entityID = entityID;
    }

    public String getBackgroundProcessDescriptor() {
        return backgroundProcessDescriptor;
    }

    public void setBackgroundProcessDescriptor(String backgroundProcessDescriptor) {
        this.backgroundProcessDescriptor = backgroundProcessDescriptor;
    }

    public org.touchhome.bundle.api.thread.BackgroundProcessStatus getStatus() {
        return status;
    }

    public void setStatus(org.touchhome.bundle.api.thread.BackgroundProcessStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getBackgroundProcessServiceID() {
        return backgroundProcessServiceID;
    }

    public void setBackgroundProcessServiceID(String backgroundProcessServiceID) {
        this.backgroundProcessServiceID = backgroundProcessServiceID;
    }

    public String getTitle() {
        String title = "Status: " + status + ". Descriptor: " + backgroundProcessDescriptor;
        if (StringUtils.isNotEmpty(errorMessage)) {
            title += ". RF24Message: " + errorMessage;
        }
        return title;
    }
}
