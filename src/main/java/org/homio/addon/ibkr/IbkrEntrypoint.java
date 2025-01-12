package org.homio.addon.ibkr;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.springframework.stereotype.Component;

import java.net.URL;

@Log4j2
@Component
@RequiredArgsConstructor
public class IbkrEntrypoint implements AddonEntrypoint {

  @SneakyThrows
  public void init() {

  }

  @Override
  public URL getAddonImageURL() {
    return getResource("images/ibkr.png");
  }
}
