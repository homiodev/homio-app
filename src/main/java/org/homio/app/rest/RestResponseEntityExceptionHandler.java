package org.homio.app.rest;

import java.nio.file.DirectoryNotEmptyException;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.homio.bundle.api.model.ErrorHolderModel;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.hquery.api.HardwareException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;

@Log4j2
@ControllerAdvice
@RestController
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({HardwareException.class})
    public ErrorHolderModel handleHardwareException(HardwareException ex) {
        log.error("Error <{}>", CommonUtils.getErrorMessage(ex));
        return new ErrorHolderModel("Hardware error", String.join("; ", ex.getInputs()), ex);
    }

    @ExceptionHandler({DirectoryNotEmptyException.class})
    public ErrorHolderModel handleDirectoryNotEmptyException(DirectoryNotEmptyException ex) {
        String msg = CommonUtils.getErrorMessage(ex);
        return new ErrorHolderModel(
            "Unable remove directory", "Directory " + msg + " not empty", ex);
    }

    @Override
    protected @NotNull ResponseEntity<Object> handleExceptionInternal(
        @NotNull Exception ex, Object body, @NotNull HttpHeaders headers, @NotNull HttpStatus status, @NotNull WebRequest request) {
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
        }
        String msg = CommonUtils.getErrorMessage(ex);
        if (ex instanceof NullPointerException) {
            msg += ". src: " + ex.getStackTrace()[0].toString();
        }
        log.error("Error <{}>", msg, ex);
        Objects.requireNonNull(((ServletWebRequest) request).getResponse())
               .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new ResponseEntity<>(new ErrorHolderModel("Error", msg, ex), headers, status);
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<Object> handleUnknownException(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
