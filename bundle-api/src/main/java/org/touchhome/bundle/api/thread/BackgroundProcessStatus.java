package org.touchhome.bundle.api.thread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum BackgroundProcessStatus {
    STOP,
    RUNNING,
    NEVER_RUN,
    FAILED,
    RESTARTING, // uses when we want stop task and run it (shouldRUn will return true)
    EXECUTED,
    LISTENING; // listen for incoming messages, etc...

    static {
        STOP.followStatuses = new BackgroundProcessStatus[]{RUNNING, FAILED, RESTARTING};
        RUNNING.followStatuses = new BackgroundProcessStatus[]{STOP, FAILED, RESTARTING};
        NEVER_RUN.followStatuses = new BackgroundProcessStatus[]{FAILED, RUNNING, STOP, RESTARTING};
        FAILED.followStatuses = new BackgroundProcessStatus[]{FAILED, RUNNING, EXECUTED, STOP, RESTARTING};
        EXECUTED.followStatuses = new BackgroundProcessStatus[]{RUNNING, FAILED, RESTARTING};
        RESTARTING.followStatuses = new BackgroundProcessStatus[]{RUNNING, FAILED};
    }

    private BackgroundProcessStatus[] followStatuses;

    @JsonCreator
    public static BackgroundProcessStatus fromValue(String value) {
        return Stream.of(BackgroundProcessStatus.values()).filter(scriptStatus -> scriptStatus.name().equals(value)).findFirst().orElse(null);
    }

    public void assertFollowStatus(BackgroundProcessStatus backgroundProcessStatus) {
        for (BackgroundProcessStatus followStatuses : followStatuses) {
            if (followStatuses.equals(backgroundProcessStatus)) {
                return;
            }
        }
        throw new IllegalStateException("Wrong Script follow status from: " + this.name() + " to: " + backgroundProcessStatus.name());
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
