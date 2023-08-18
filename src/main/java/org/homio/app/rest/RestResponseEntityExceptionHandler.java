package org.homio.app.rest;

import java.nio.file.DirectoryNotEmptyException;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.model.ErrorHolderModel;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.hquery.api.HardwareException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Log4j2
@ControllerAdvice
@RestController
@RequiredArgsConstructor
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private final EntityContextImpl entityContext;

    @ExceptionHandler({LinkageError.class})
    public ErrorHolderModel handleLinkageError(LinkageError ex) {
        log.error("Error <{}>", CommonUtils.getErrorMessage(ex));
        return new ErrorHolderModel("Linkage error", ex.getMessage(), ex);
    }

    @ExceptionHandler({HardwareException.class})
    public ErrorHolderModel handleHardwareException(HardwareException ex) {
        log.error("Error <{}>", CommonUtils.getErrorMessage(ex));
        return new ErrorHolderModel("Hardware error", String.join("; ", ex.getInputs()), ex);
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        log.error("Error <{}>", CommonUtils.getErrorMessage(ex));
        return new ResponseEntity<>(new ErrorHolderModel("ERROR", "W.ERROR.ACCESS_DENIED", ex), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({DirectoryNotEmptyException.class})
    public ErrorHolderModel handleDirectoryNotEmptyException(DirectoryNotEmptyException ex) {
        String msg = CommonUtils.getErrorMessage(ex);
        return new ErrorHolderModel(
                "Unable remove directory", "Directory " + msg + " not empty", ex);
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<Object> handleUnknownException(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NotNull Exception ex,
            @Nullable Object body,
            @NotNull HttpHeaders headers,
            @NotNull HttpStatusCode statusCode,
            @NotNull WebRequest request) {
        String msg = CommonUtils.getErrorMessage(ex);
        // addon linkage error
        if (ex.getCause() instanceof AbstractMethodError && msg.contains("org.homio.addon")) {
            int start = msg.indexOf("org.homio.addon") + "org.homio.addon".length() + 1;
            entityContext.getAddon().disableAddon(msg.substring(start, msg.indexOf(".", start)));
        }
        if (ex instanceof NullPointerException) {
            msg += ". src: " + ex.getStackTrace()[0].toString();
        }
        log.error("Error <{}>", msg, ex);
        Objects.requireNonNull(((ServletWebRequest) request).getResponse())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new ResponseEntity<>(new ErrorHolderModel("ERROR", msg, ex), headers, statusCode);
    }
}
