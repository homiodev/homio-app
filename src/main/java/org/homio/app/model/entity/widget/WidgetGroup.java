package org.homio.app.model.entity.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum WidgetGroup {
  Simple("fab fa-simplybuilt"),
  Chart("fas fa-chart-simple"),
  Media("fas fa-compact-disc"),
  Finance("fas fa-coins"),
  Social("fas fa-facebook"),
  Education("fas fa-user-graduate"),
  Entertainment("fas fa-gamepad"),
  Health("fas fa-kit-medical");

  @Getter
  private final String icon;
}
