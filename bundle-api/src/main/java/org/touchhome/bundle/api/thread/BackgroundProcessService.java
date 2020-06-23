package org.touchhome.bundle.api.thread;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.manager.LoggerManager;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.PrintStream;
import java.util.Date;

import static org.touchhome.bundle.api.thread.BackgroundProcessStatus.*;

public abstract class BackgroundProcessService<ReturnType> implements Comparable<BackgroundProcessService> {

    protected final Logger log = LogManager.getLogger(getClass());
    protected final String id;
    protected final EntityContext entityContext;
    protected final LoggerManager loggerManager;
    @Getter
    private final Date creationTime = new Date();
    protected PrintStream printStream;
    private Logger logger;
    private BackgroundProcessStatus status;
    private String errorMessage;
    @Setter
    private boolean writeLogsToFile = false;
    @Setter
    private boolean writeLogsToLog4j2 = true;
    @Setter
    @Getter
    private String state;

    public BackgroundProcessService(String id, EntityContext entityContext) {
        this.id = id;
        this.entityContext = entityContext;
        this.loggerManager = entityContext.getBean(LoggerManager.class);
        log.info("Create BGP service: <{}>", id);
    }

    protected abstract ReturnType runInternal() throws Exception;

    public final ReturnType run() throws Exception {
        try {
            ReturnType returnType = runInternal();
            this.status = EXECUTED;
            this.errorMessage = null;

            return returnType;
        } catch (DataIntegrityViolationException ex) {
            logError("Severe error!!!!!", ex);
            setStatus(FAILED, ex.getMessage());
        } catch (Exception ex) {
            setStatus(FAILED, ex.getMessage());
            logError("Error while evaluate bgp with key: '" + getName() + "'", ex);
            if (onException(ex)) {
                releaseResources();
                throw ex;
            }
        }
        return null;
    }

    // true - stop thread; false - ignore exception
    public abstract boolean onException(Exception ex);

    // Period in milliseconds
    public abstract long getPeriod();

    public abstract boolean shouldStartNow();

    public ScheduleType getScheduleType() {
        return ScheduleType.Delay;
    }

    public final String getId() {
        return id;
    }

    public String getName() {
        return getClass().getSimpleName();
    }

    public final boolean canWorkSafe() {
        try {
            return canWork();
        } catch (Exception ex) {
            logWarning("Error while call canWork: " + TouchHomeUtils.getErrorMessage(ex));
            return false;
        }
    }

    public abstract boolean canWork();

    /**
     * Should we start this bgp if other obstacles solved
     */
    protected abstract boolean isAutoStart();

    @Override
    public final int compareTo(BackgroundProcessService backgroundProcessService) {
        return getId().compareTo(backgroundProcessService.getId());
    }

    @Override
    public final boolean equals(Object o) {
        return this.getId().equals(((BackgroundProcessService) o).getId());
    }

    @Override
    public final int hashCode() {
        return this.getId().hashCode();
    }

    public void afterStop() {
    }

    public void beforeStart() {
    }

    public void setStatus(BackgroundProcessStatus status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public final void beforeStartService() {
        try {
            beforeStart();
        } catch (Exception ex) {
            String warning = "Error while call beforeStart for service: " + getName();
            logWarning(warning);
            releaseResources();
        }
        setStatus(RUNNING, "");
    }

    public final void cancelService() {
        try {
            afterStop();
            releaseResources();
            setStatus(STOP, "");
        } catch (Exception ex) {
            String warning = "Error while call afterStop for service: " + getName();
            logWarning(warning);
            releaseResources();
            setStatus(FAILED, warning);
        }
    }

    public BackgroundProcessStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String whyCannotWork() {
        return null;
    }

    private Logger getLogger() {
        if (logger == null) {
            logger = createLogger();
        }
        return logger;
    }

    public Logger createLogger() {
        if (printStream == null) {
            return loggerManager.getLogger(getClass().getSimpleName(), getClass().getSimpleName());
        } else {
            return loggerManager.getLogger(printStream);
        }
    }

    public final void logInfo(String message, Object... params) {
        writeLog(Level.INFO, message, params);
    }

    public final void logWarning(String message, Object... params) {
        writeLog(Level.WARN, message, params);
    }

    public final void logError(String message, Object... params) {
        writeLog(Level.ERROR, message, params);
    }

    private void writeLog(Level level, String message, Object... params) {
        if (writeLogsToFile && getLogger() != null) {
            getLogger().log(level, message, params);
        }
        if (writeLogsToLog4j2) {
            log.log(level, message, params);
        }
    }

    @Override
    public final String toString() {
        return getName();
    }

    public final void setPrintStream(PrintStream printStream) {
        this.printStream = printStream;
    }

    public final void fireStartEvent() {
        if (getStatus() == BackgroundProcessStatus.RESTARTING) {
            setStatus(BackgroundProcessStatus.RUNNING, null);
        }
    }

    public void releaseResources() {
    }

    public String getDescription() {
        return "";
    }

    public enum ScheduleType {
        Delay, Rate
    }
}
