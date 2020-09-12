package org.touchhome.app.json.bgp;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.field.UIField;

import java.util.Date;

import static org.touchhome.bundle.api.ui.field.UIFieldType.StaticDate;

@Getter
@Setter
public class BackgroundProcessServiceJSON implements HasEntityIdentifier {

    private String entityID;

    @UIField(readOnly = true, order = 1)
    private String processName;

    @UIField(readOnly = true, order = 3)
    private String description;

    @UIField(order = 4, readOnly = true, type = StaticDate)
    private Date creationTime;

    @UIField(readOnly = true, order = 5)
    private String state;

    @UIField(readOnly = true, order = 19)
    private String scheduleType;

    @UIField(readOnly = true, order = 20)
    private Long timeToNextSchedule;

    @UIField(readOnly = true, order = 21)
    private Status status;

    @UIField(readOnly = true, order = 22)
    private String errorMessage;

    @UIField(readOnly = true, order = 23)
    private Long period;

    @UIField(readOnly = true, order = 24)
    private int runCount;
}
