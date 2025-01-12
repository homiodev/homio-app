package org.homio.app.rest;

import lombok.RequiredArgsConstructor;
import org.homio.api.model.OptionModel;
import org.homio.app.manager.common.ContextImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/rest/hardware")
@RequiredArgsConstructor
public class HardwareController {

  private final ContextImpl context;

  @GetMapping("/event")
  public Collection<OptionModel> getHardwareEvents() {
    return context.event().getEvents();
  }
}
