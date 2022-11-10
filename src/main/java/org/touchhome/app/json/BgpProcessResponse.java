package org.touchhome.app.json;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import org.json.JSONObject;
import org.touchhome.app.manager.common.impl.EntityContextBGPImpl;

@Getter
public class BgpProcessResponse {

  private final List<BgpProcess> processes = new ArrayList<>();

  public void add(EntityContextBGPImpl.ThreadContextImpl<?> context) {
    this.processes.add(new BgpProcess(context));
  }

  @Getter
  private static class BgpProcess {

    private final String name;
    private final Long period;
    private final String error;
    private final String description;
    private final String scheduleType;
    private final Date date;
    private final int runCount;
    private final String state;
    private final JSONObject metadata;
    private final String nextCall;

    public BgpProcess(EntityContextBGPImpl.ThreadContextImpl<?> context) {
      this.name = context.getName();
      this.period = context.getPeriod() == null ? null : context.getPeriod().toMillis();
      this.error = context.getError();
      this.description = context.getDescription();
      this.scheduleType = context.getScheduleType().name();
      this.date = context.getCreationTime();
      this.runCount = context.getRunCount();
      this.state = context.getState();
      this.metadata = context.getMetadata();
      this.nextCall = context.getTimeToNextSchedule();
    }
  }
}
