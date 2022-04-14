package org.touchhome.app.extloader;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ResourceUtils;
import org.touchhome.common.exception.ServerException;

import java.net.URL;
import java.net.URLClassLoader;

public class ExtUtil {

    public static Class getClass(Object object) {
        return AopUtils.getTargetClass(object);
    }

    static boolean isJar(String fileName) {
        return fileName != null && FilenameUtils.getExtension(fileName).equals(ResourceUtils.URL_PROTOCOL_JAR);
    }

    static void addToClasspath(URL url) {
        try {
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            MethodUtils.invokeMethod(classLoader, true, "addURL", url);
        } catch (Exception e) {
            throw new ServerException("Unexpected exception", e);
        }
    }
}
