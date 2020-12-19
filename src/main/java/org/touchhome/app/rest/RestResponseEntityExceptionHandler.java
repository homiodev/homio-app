package org.touchhome.app.rest;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.hquery.api.HardwareException;
import org.touchhome.bundle.api.model.ErrorHolderModel;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Log4j2
@ControllerAdvice
@RestController
public class RestResponseEntityExceptionHandler {

    @ExceptionHandler({Exception.class})
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorHolderModel handleException(Exception ex) {
        String msg;
        if (ex instanceof NullPointerException || ex.getCause() instanceof NullPointerException) {
            msg = ex.getStackTrace()[0].toString();
        } else {
            msg = StringUtils.defaultString(ex.getMessage(), ex.toString());
        }
        log.error("Error <{}>", msg);
        return new ErrorHolderModel("Error", msg, ex);
    }

    @ExceptionHandler({HardwareException.class})
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorHolderModel handleHardwareException(HardwareException ex) {
        log.error("Error <{}>", TouchHomeUtils.getErrorMessage(ex));
        return new ErrorHolderModel("Hardware error", String.join("; ", ex.getInputs()), ex);
    }
}
