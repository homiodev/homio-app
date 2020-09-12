package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.ScriptManager;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.repository.ScriptRepository;
import org.touchhome.bundle.api.util.TouchHomeUtils;

// TODO: refactor this !!!!!!!!!!!!!!!!!!!!!!!!!!
@Log4j2
@RestController
@RequestMapping("/rest/background")
@RequiredArgsConstructor
public class BackgroundProcessController {

    private final ScriptRepository scriptRepository;
    private final ScriptManager scriptManager;

    @GetMapping("dynamic/{url}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void dynamicCall(@PathVariable String url, @RequestParam(value = "json", required = false) String json) throws Exception {
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
        scriptManager.startThread(scriptEntity, json, true, null, false);
    }

    /*@GetMapping("progress")
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
    }*/

    @GetMapping("dynamic/stop/{url}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void stopScriptByName(@PathVariable String url) {
        ScriptEntity scriptEntity = scriptRepository.getByURL(url);
        if(scriptEntity != null) {
            log.info("Stop script: " + scriptEntity.getEntityID());
            scriptManager.stopThread(scriptEntity);
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
