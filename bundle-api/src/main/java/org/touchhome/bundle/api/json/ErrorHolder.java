package org.touchhome.bundle.api.json;

import lombok.Getter;
import org.touchhome.bundle.api.util.SmartUtils;

@Getter
public class ErrorHolder {
    private String message;
    private String cause;
    private String errorType;

    public ErrorHolder(String message, Exception ex) {
        this.message = message;
        this.cause = SmartUtils.getErrorMessage(ex);
        this.errorType = ex.getClass().getSimpleName();
    }
}
