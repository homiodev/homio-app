package org.homio.app.config;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Iterator;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.repository.WorkspaceVariableRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

@Log4j2
@Profile("demo")
@Configuration
@Deprecated // TODO: DROP asap
public class DemoConfiguration {

    @Autowired
    private Context context;

    @Scheduled(fixedDelay = 600000)
    private void clearOutdatedData() {
        try {
            log.info("demo profile: clear");
            removeItems(WidgetBaseEntity.class, ScriptEntity.class);

            clearWidgets();
            clearWorkspace();
        } catch (Exception ex) {
            log.error("Error while clean demo data", ex);
        }
    }

    private void removeItems(Class<? extends BaseEntity>... classes) {
        for (Class<? extends BaseEntity> aClass : classes) {
            for (BaseEntity baseEntity : context.db().findAll(aClass)) {
                if (quiteOld(baseEntity)) {
                    context.db().delete(baseEntity);
                }
            }
        }
    }

    private void clearWidgets() {
        for (WidgetTabEntity tab : context.db().findAll(WidgetTabEntity.class)) {
            if (!WidgetTabEntity.MAIN_TAB_ID.equals(tab.getEntityID()) && tab.getWidgetBaseEntities().isEmpty()) {
                context.db().delete(tab);
            }
        }
    }

    private boolean quiteOld(BaseEntity baseEntity) {
        return MILLISECONDS.toMinutes(System.currentTimeMillis() - baseEntity.getEntityUpdated().getTime()) > 10;
    }

    private void clearWorkspace() {
        context.getBean(WorkspaceVariableRepository.class).deleteAll();

        /*for (WorkspaceEntity tab : context.db().findAll(WorkspaceEntity.class)) {
            if (!tab.getName().equals(WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME) && quiteOld(tab)) {
                // first clean workspace
                context.db().save(tab.setContent(""));
                // then drop it
                context.db().delete(tab);
            }
        }*/
    }

    private void removeOutdatedVariables(JSONObject list, String repositoryPrefix) {
        if (list != null) {
            for (Iterator<String> iterator = list.keys(); iterator.hasNext(); ) {
                String id = iterator.next();
                BaseEntity entity = context.db().getEntity(repositoryPrefix + id);
                if (entity != null &&
                        MILLISECONDS.toMinutes(System.currentTimeMillis() - entity.getEntityUpdated().getTime()) > 10) {
                    context.db().delete(entity);
                    iterator.remove();
                }
            }
        }
    }
}
