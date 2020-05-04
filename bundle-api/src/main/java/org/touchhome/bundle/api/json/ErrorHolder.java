package org.touchhome.bundle.api.json;

import lombok.Getter;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Getter
public class ErrorHolder {
    private String message;
    private String cause;
    private String errorType;

    public ErrorHolder(String message, Exception ex) {
        this.message = message;
        this.cause = TouchHomeUtils.getErrorMessage(ex);
        this.errorType = ex.getClass().getSimpleName();
    }
}
