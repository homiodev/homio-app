package org.homio.app;

import static org.junit.Assert.assertNotNull;

import org.homio.api.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles(profiles = {"test"})
public class StartupTest {

  @Autowired
  private Context context;

  @Test
  public void testStartup() {
    assertNotNull(context);
  }
}
