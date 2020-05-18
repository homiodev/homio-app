package org.touchhome.app.rest;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.json.ErrorHolder;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Log4j2
@ControllerAdvice
@RestController
public class RestResponseEntityExceptionHandler {

    @ExceptionHandler({Exception.class})
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorHolder handleException(Exception ex) {
        log.error("Error", TouchHomeUtils.getErrorMessage(ex), ex);
        return new ErrorHolder(TouchHomeUtils.getErrorMessage(ex), ex);
    }
}
