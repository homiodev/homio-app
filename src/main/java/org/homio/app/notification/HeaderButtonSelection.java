package org.homio.app.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.model.Icon;

@Getter
@RequiredArgsConstructor
public class HeaderButtonSelection {
  private final String name;
  private final Icon icon;
  private final String page;
}
