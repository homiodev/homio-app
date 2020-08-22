package org.touchhome.app.extloader;

import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.xeustechnologies.jcl.JarClassLoader;
import sun.net.www.ParseUtil;
import sun.net.www.protocol.jar.JarURLConnection;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public class SingleBundleClassLoader extends JarClassLoader {
    private final URL baseUrl;
    private final JarFile jarFile;
    private final Map<String, URL> resourceMap = new HashMap<>();
    private final Map<URL, List<URLConnection>> resourceConnections = new HashMap<>();

    @SneakyThrows
    SingleBundleClassLoader(Path jarPath) {
        super.add(jarPath.toString());
        this.jarFile = new JarFile(jarPath.toFile());
        this.baseUrl = new URL("jar", "", jarPath.toUri().toURL().toString() + "!/");
    }

    @Override
    public Enumeration<URL> getResources(String name) {
        URL resource = getResource(name);
        return resource == null ? Collections.emptyEnumeration() : Collections.enumeration(Collections.singletonList(resource));
    }

    @Override
    protected URL findResource(String name) {
        return this.getResource(name);
    }

    @Override
    @SneakyThrows
    public URL getResource(String name) {
        if (!resourceMap.containsKey(name)) {
            URL url = classpathResources.getResourceURL(name);
            if (url == null) {
                try {
                    if (this.jarFile.getEntry(name) != null) {
                        url = new URL(this.baseUrl, ParseUtil.encodePath(name, false));
                    }
                } catch (Exception ignore) {
                }
            }

            if (url != null) {
                FieldUtils.writeField(url, "handler", new sun.net.www.protocol.jar.Handler() {
                    @Override
                    protected URLConnection openConnection(URL url) throws IOException {
                        URLConnection urlConnection = super.openConnection(url);
                        resourceConnections.putIfAbsent(url, new ArrayList<>());
                        resourceConnections.get(url).add(urlConnection);
                        return urlConnection;
                    }
                }, true);
            }
            resourceMap.put(name, url);
        }

        return resourceMap.get(name);
    }

    @SneakyThrows
    void destroy() {
        for (String className : new ArrayList<>(this.classes.keySet())) {
            this.unloadClass(className);
        }
        for (List<URLConnection> urlConnections : resourceConnections.values()) {
            for (URLConnection urlConnection : urlConnections) {
                ((JarURLConnection) urlConnection).getJarFile().close();
            }
        }
        this.jarFile.close();
    }
}
