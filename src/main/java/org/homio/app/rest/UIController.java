package org.homio.app.rest;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextMediaImpl;
import org.homio.app.model.rest.DynamicUpdateRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/rest/ui")
@RequiredArgsConstructor
public class UIController {
    private final ContextImpl context;

    @PutMapping("/multiDynamicUpdates")
    public void multiDynamicUpdates(@Valid @RequestBody List<DynamicRequestItem> request) {
        for (DynamicRequestItem requestItem : request) {
            try {
                context.ui().registerForUpdates(new DynamicUpdateRequest(requestItem.did, requestItem.eid));
            } catch (Exception ignored) {
            }
        }
    }

    @DeleteMapping("/dynamicUpdates")
    public void unregisterForUpdates(@Valid @RequestBody DynamicUpdateRequest request) {
        context.ui().unRegisterForUpdates(request);
    }

    @PostMapping("/web-driver/{entityID}/interact")
    public void interactWebDriver(
            @PathVariable("entityID") String entityID,
            @RequestBody ContextMediaImpl.InteractWebDriverRequest request) {
        context.media().interactWebDriver(entityID, request);
    }


    @Getter
    @Setter
    public static class DynamicRequestItem {

        private String eid;
        private String did;
    }
}
