package org.touchhome.app.extloader;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExtUtil {

    public static Class getClass(Object object) {
        return AopUtils.getTargetClass(object);
    }

    static Path unpack(Path pluginPath) throws IOException {
        if (!Files.exists(pluginPath)) {
            throw new RuntimeException("File: " + pluginPath + " not found");
        }
        Path tempDirectory = Files.createTempDirectory("Batch_unpack_" + pluginPath.getFileName().toString()
                .substring(0, pluginPath.getFileName().toString().length() - 4) + "__");

        java.util.jar.JarFile jar = new java.util.jar.JarFile(pluginPath.toFile());
        java.util.Enumeration enumEntries = jar.entries();
        while (enumEntries.hasMoreElements()) {
            java.util.jar.JarEntry file = (java.util.jar.JarEntry) enumEntries.nextElement();
            Path path = tempDirectory.resolve(file.getName());
            if (Files.isDirectory(path)) {
                Files.createDirectories(path);
                continue;
            } else {
                try {
                    Files.createDirectories(path.getParent());
                } catch (FileAlreadyExistsException ignore) {
                }
            }
            if (!file.isDirectory()) {
                Files.copy(jar.getInputStream(file), path);
            }
        }
        jar.close();
        return tempDirectory;
    }

    static boolean isJar(String fileName) {
        return fileName != null && FilenameUtils.getExtension(fileName).equals(ResourceUtils.URL_PROTOCOL_JAR);
    }

    public static void addToClasspath(URL url) {
        try {
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            MethodUtils.invokeMethod(classLoader, true, "addURL", url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }
}
