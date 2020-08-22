package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.json.bgp.BackgroundProcessStatusJSON;
import org.touchhome.app.manager.BackgroundProcessManager;
import org.touchhome.app.manager.ScriptManager;
import org.touchhome.app.model.entity.HasBackgroundProcesses;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.repository.ScriptRepository;
import org.touchhome.app.thread.js.AbstractJSBackgroundProcessService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.thread.BackgroundProcessService;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/rest/background")
@RequiredArgsConstructor
public class BackgroundProcessController {

    private final ScriptRepository scriptRepository;
    private final EntityContext entityContext;
    private final BackgroundProcessManager backgroundProcessManager;
    private final ScriptManager scriptManager;

    @GetMapping("dynamic/{url}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public Object dynamicCall(@PathVariable String url, @RequestParam(value = "json", required = false) String json) throws Exception {
        ScriptEntity scriptEntity = scriptRepository.getByURL(url);
        if (scriptEntity == null) {
            throw new RuntimeException("Dynamic URL: '" + url + "' not exists.");
        }
        if (StringUtils.isEmpty(scriptEntity.getJavaScript())) {
            throw new RuntimeException("Script not valid for dynamic URL: '" + url + "'.");
        }
        if (StringUtils.isEmpty(scriptEntity.getJavaScript())) {
            throw new RuntimeException("Script not valid for dynamic URL: '" + url + "'.");
        }
        return scriptManager.startThread(scriptEntity, json, true, null, false);
    }

    @GetMapping("progress")
    public List<BackgroundProcessStatusJSON> getProgressValue(@RequestParam("values") String values) {
        List<BackgroundProcessStatusJSON> backgroundProcessStatusJSONList = new ArrayList<>();
        for (String entityIDAndKey : values.split(";")) {

            int i = entityIDAndKey.lastIndexOf(":");
            String entityID = entityIDAndKey.substring(0, i);
            String scriptDescriptor = entityIDAndKey.substring(i + 1);

            BaseEntity baseEntity = entityContext.getEntity(entityID);
            if (baseEntity instanceof HasBackgroundProcesses) {
                ScriptEntity scriptEntity = ((HasBackgroundProcesses) baseEntity).getBackgroundProcessScript(scriptDescriptor);
                if (scriptEntity != null) {
                    String backgroundProcessServiceID = scriptEntity.getBackgroundProcessServiceID();


                    BackgroundProcessService bgp = backgroundProcessManager.getBackgroundProcessByDescriptorID(backgroundProcessServiceID);
                    BackgroundProcessStatusJSON backgroundProcessStatusJSON = new BackgroundProcessStatusJSON();
                    backgroundProcessStatusJSON.setEntityID(entityID);
                    backgroundProcessStatusJSON.setBackgroundProcessDescriptor(scriptDescriptor);
                    BackgroundProcessStatus scriptStatus = bgp == null ? scriptEntity.getBackgroundProcessStatus() : bgp.getStatus();
                    backgroundProcessStatusJSON.setStatus(scriptStatus);
                    if (scriptStatus == BackgroundProcessStatus.FAILED) {
                        backgroundProcessStatusJSON.setErrorMessage(bgp == null ? scriptEntity.getError() : bgp.getErrorMessage());
                    }
                    backgroundProcessStatusJSON.setProgress(backgroundProcessManager.isRunning(bgp) ?
                            backgroundProcessManager.getTimeInPercentageToNextSchedule(bgp) : 0);
                    backgroundProcessStatusJSONList.add(backgroundProcessStatusJSON);
                }
            }
        }
        return backgroundProcessStatusJSONList;
    }

    @GetMapping("dynamic/stop/{url}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public BackgroundProcessStatus stopScriptByName(@PathVariable String url) {
        return scriptManager.stopThread(scriptRepository.getByURL(url));
    }

    @GetMapping("backgroundProcessStatuses")
    public List<BackgroundProcessStatusJSON> getBackgroundProcessStatuses(@RequestParam("entityID") String entityID) {
        List<BackgroundProcessStatusJSON> backgroundProcessStatusJSONS = new ArrayList<>();

        BaseEntity item = entityContext.getEntity(entityID);
        if (item instanceof HasBackgroundProcesses) {
            HasBackgroundProcesses hasBackgroundProcesses = (HasBackgroundProcesses) item;
            Set<ScriptEntity> availableBackgroundProcesses = hasBackgroundProcesses.getAvailableProcesses();
            for (ScriptEntity availableBackgroundProcess : availableBackgroundProcesses) {
                buildBackgroundProcessStatus(item, availableBackgroundProcess, backgroundProcessStatusJSONS);
            }
        }

        //    List<Class<? extends AbstractJSBackgroundProcessService>> classList = ClassFinder.getClassesWithParentAndTypeArgument(AbstractJSBackgroundProcessService.class, item.getClass(), 1);
        //  classList.forEach(aClass -> buildBackgroundProcessStatus(entityID, backgroundProcessStatusJSONS, item, aClass));
        return backgroundProcessStatusJSONS;
    }

    private void buildBackgroundProcessStatus(BaseEntity item, ScriptEntity scriptEntity, List<BackgroundProcessStatusJSON> backgroundProcessStatusJSONS) {
        try {
            AbstractJSBackgroundProcessService abstractJSBackgroundProcessService = scriptEntity.createBackgroundProcessService(entityContext);

            BackgroundProcessStatusJSON backgroundProcessStatusJSON = new BackgroundProcessStatusJSON();
            backgroundProcessStatusJSON.setEntityID(item.getEntityID());
            backgroundProcessStatusJSON.setBackgroundProcessServiceID(scriptEntity.getBackgroundProcessServiceID());
            backgroundProcessStatusJSON.setBackgroundProcessDescriptor(scriptEntity.getDescription());

            if (!abstractJSBackgroundProcessService.canWork()) {
                backgroundProcessStatusJSON.setErrorMessage(abstractJSBackgroundProcessService.whyCannotWork());
                backgroundProcessStatusJSON.setStatus(BackgroundProcessStatus.FAILED);
            } else if (backgroundProcessManager.isRunning(scriptEntity.getBackgroundProcessServiceID())) {
                backgroundProcessStatusJSON.setStatus(BackgroundProcessStatus.RUNNING);
                backgroundProcessStatusJSON.setErrorMessage(backgroundProcessStatusJSON.getBackgroundProcessDescriptor() + " working");
            } else {
                backgroundProcessStatusJSON.setStatus(abstractJSBackgroundProcessService.getStatus());
                backgroundProcessStatusJSON.setErrorMessage(abstractJSBackgroundProcessService.getErrorMessage());
            }
            backgroundProcessStatusJSONS.add(backgroundProcessStatusJSON);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /*private void buildBackgroundProcessStatus(String entityID, List<BackgroundProcessStatusJSON> backgroundProcessStatuses, BaseEntity item, Class<? extends AbstractJSBackgroundProcessService> aClass) {
        try {
            AbstractJSBackgroundProcessService backgroundProcessService = aClass.getConstructor(item.getClass(), InternalManager.class).newInstance(item, context);
            BackgroundProcessStatusJSON backgroundProcessStatus = new BackgroundProcessStatusJSON();
            backgroundProcessStatus.setEntityID(entityID);
            backgroundProcessStatus.setBackgroundProcessDescriptor(backgroundProcessService.getClass().getSimpleName());
            if (!backgroundProcessService.canWork()) {
                backgroundProcessStatus.setErrorMessage(backgroundProcessService.whyCannotWork());
                backgroundProcessStatus.setStatus(BackgroundProcessStatusJSON.FAILED);
                backgroundProcessStatuses.add(backgroundProcessStatus);
            } else if (backgroundProcessManager.isRunning(backgroundProcessService.getCommandIndex())) {
                backgroundProcessStatus.setStatus(BackgroundProcessStatusJSON.RUNNING);
                backgroundProcessStatus.setErrorMessage(backgroundProcessStatus.getBackgroundProcessDescriptor() + " working");
                backgroundProcessStatuses.add(backgroundProcessStatus);
            } else if (backgroundProcessService.shouldStartNow()) {
                LOG.info("Somewhere bug. Restart bgp: " + backgroundProcessService.getServiceDescription());
                backgroundProcessManager.scheduleAtFixedRate(backgroundProcessService);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }*/
}
