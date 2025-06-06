package org.homio.app.chromecast;

import lombok.Setter;
import org.homio.api.Context;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.litvak.chromecast.api.v2.MediaStatus;

import java.util.function.Function;
import java.util.function.Supplier;

@Setter
public class ChromecastEndpoint extends BaseDeviceEndpoint<ChromecastEntity> {

  @Nullable private Function<MediaStatus, State> mediaStatusReader;

  @Nullable private Function<su.litvak.chromecast.api.v2.Status, State> statusReader;

  @Nullable private Supplier<State> closeEventReader;

  public ChromecastEndpoint(@NotNull Context context) {
    super("CHROMECAST", context);
  }

  public void setValue(@Nullable State value) {
    setValue(value, true);
  }

  @Override
  public String getVariableGroupID() {
    return "video-" + getDeviceID();
  }

  public void fireEvent(MediaStatus status) {
    if (mediaStatusReader != null) {
      setValue(mediaStatusReader.apply(status), true);
    }
  }

  public void fireEvent(su.litvak.chromecast.api.v2.Status status) {
    if (statusReader != null) {
      setValue(statusReader.apply(status), true);
    }
  }

  public void fireCloseEvent() {
    if (closeEventReader != null) {
      setValue(closeEventReader.get(), true);
    }
  }
}
