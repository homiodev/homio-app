package org.touchhome.app.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.repository.device.AllDeviceRepository;

@RestController
@RequestMapping("/rest")
public class WireController {

  @Autowired
  private AllDeviceRepository allDeviceRepository;

  /*  @RequestMapping(value = "/getW1LinkStatus", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public LinkStatus getW1LinkStatus(@RequestParam(value = "entityID") String entityID) {
        BaseEntity linkToEntityID = allDeviceRepository.getLinkToEntityID(entityID);
        LinkStatus linkStatus = new LinkStatus();
        linkStatus.setLinkedEntityID(linkToEntityID == null ? null : linkToEntityID.getEntityID());
        linkStatus.setLinkedPage(linkToEntityID == null ? null : SidebarMenuItem.getHref(linkToEntityID.getClass()));
        return linkStatus;
    }*/

    /*@RequestMapping(value = "/get1WireSensors", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OptionModel> get1WireSensors(@RequestParam(value = "onlyUnused", defaultValue = "false") boolean onlyUnused)
            throws IOException {
        return w1Manager.getNotRegisteredSensors(onlyUnused);
    }*/
}

















