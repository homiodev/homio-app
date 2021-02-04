package org.touchhome.app.videoStream.onvif.util;

import org.touchhome.app.videoStream.handler.IpCameraGroupHandler;
import org.touchhome.app.videoStream.handler.OnvifCameraHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * used so a 'group' thing can get a handle to each cameras handler, and the group and cameras can talk to each other.
 */
public class GroupTracker {
    public Map<String, OnvifCameraHandler> onlineCameraMap = new HashMap<>();

   // public ArrayList<IpCameraHandler> listOfOnlineCameraHandlers = new ArrayList<>(1);
    public ArrayList<IpCameraGroupHandler> listOfGroupHandlers = new ArrayList<>(0);
   // public ArrayList<String> listOfOnlineCameraUID = new ArrayList<>(1);
}
