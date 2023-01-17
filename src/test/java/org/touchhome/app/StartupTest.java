package org.touchhome.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.touchhome.app.setting.console.lines.ConsoleDebugLevelSetting;
import org.touchhome.bundle.api.EntityContext;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles(profiles = {"test"})
public class StartupTest {

    @Autowired private EntityContext entityContext;

    @Test
    public void testStartup() {
        assertNotNull(entityContext);
        assertTrue(entityContext.setting().getValue(ConsoleDebugLevelSetting.class));
    }
}
