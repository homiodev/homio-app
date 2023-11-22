package org.homio.app.extloader;

import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.app.HomioClassLoader;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.ProxyClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public class AddonClassLoader extends JarClassLoader {

    private static final ProxyClassLoader jvmLoader = new JvmLoader();
    private static final long L_ENCODED = 0xF800802DFFFFFFFFL;
    private static final long H_ENCODED = 0xB800000178000000L;
    private final URL baseUrl;
    private final JarFile jarFile;
    private final Map<String, URL> resourceMap = new HashMap<>();
    private final Map<URL, List<URLConnection>> resourceConnections = new HashMap<>();

    @SneakyThrows
    public AddonClassLoader(Path jarPath) {
        super.add(jarPath.toString());
        this.jarFile = new JarFile(jarPath.toFile());
        this.baseUrl = new URL("jar", "", jarPath.toUri().toURL() + "!/");
    }

    @SneakyThrows
    public void destroy() {
        for (String className : new ArrayList<>(this.classes.keySet())) {
            this.unloadClass(className);
        }
        for (List<URLConnection> urlConnections : resourceConnections.values()) {
            for (URLConnection urlConnection : urlConnections) {
                ((java.net.JarURLConnection) urlConnection).getJarFile().close();
            }
        }
        this.jarFile.close();
    }

    @Override
    public Enumeration<URL> getResources(String name) {
        URL resource = getResource(name);
        return resource == null ? Collections.emptyEnumeration() : Collections.enumeration(Collections.singletonList(resource));
    }

    @Override
    @SneakyThrows
    public URL getResource(String name) {
        if (!resourceMap.containsKey(name)) {
            URL url = classpathResources.getResourceURL(name);
            if (url == null) {
                try {
                    if (this.jarFile.getEntry(name) != null) {
                        url = new URL(this.baseUrl, encodePath(name, false));
                    }
                } catch (Exception ignore) {
                }
            }

            if (url != null) {
                URLStreamHandler urlHandler = (URLStreamHandler) FieldUtils.readDeclaredField(url, "handler", true);
                Method jarOpenConnectionMethod = urlHandler.getClass().getDeclaredMethod("openConnection", URL.class);
                jarOpenConnectionMethod.setAccessible(true);
                FieldUtils.writeField(url, "handler", new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        try {
                            URLConnection urlConnection = (URLConnection) MethodUtils
                                    .invokeMethod(urlHandler, true, "openConnection", u);
                            resourceConnections.putIfAbsent(u, new ArrayList<>());
                            resourceConnections.get(u).add(urlConnection);
                            return urlConnection;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, true);
            }
            resourceMap.put(name, url);
        }

        return resourceMap.get(name);
    }

    @Override
    protected URL findResource(String name) {
        return this.getResource(name);
    }

    // we need sys loader to avoid issue with no class found Object, etc...
    @Override
    protected void addDefaultLoader() {
        synchronized (loaders) {
            loaders.add(jvmLoader);
            Collections.sort(loaders);
        }
    }

    private static String encodePath(String path, boolean flag) {
        if (flag && File.separatorChar != '/') {
            return encodePath(path, 0, File.separatorChar);
        } else {
            int index = firstEncodeIndex(path);
            if (index > -1) {
                return encodePath(path, index, '/');
            } else {
                return path;
            }
        }
    }

    private static String encodePath(String path, int index, char sep) {
        char[] pathCC = path.toCharArray();
        char[] retCC = new char[pathCC.length * 2 + 16 - index];
        if (index > 0) {
            System.arraycopy(pathCC, 0, retCC, 0, index);
        }
        int retLen = index;

        for (int i = index; i < pathCC.length; i++) {
            char c = pathCC[i];
            if (c == sep) {
                retCC[retLen++] = '/';
            } else {
                if (c <= 0x007F) {
                    if (c >= 'a' && c <= 'z' ||
                            c >= 'A' && c <= 'Z' ||
                            c >= '0' && c <= '9') {
                        retCC[retLen++] = c;
                    } else if (match(c, L_ENCODED, H_ENCODED)) {
                        retLen = escape(retCC, c, retLen);
                    } else {
                        retCC[retLen++] = c;
                    }
                } else if (c > 0x07FF) {
                    retLen = escape(retCC, (char) (0xE0 | ((c >> 12) & 0x0F)), retLen);
                    retLen = escape(retCC, (char) (0x80 | ((c >> 6) & 0x3F)), retLen);
                    retLen = escape(retCC, (char) (0x80 | ((c >> 0) & 0x3F)), retLen);
                } else {
                    retLen = escape(retCC, (char) (0xC0 | ((c >> 6) & 0x1F)), retLen);
                    retLen = escape(retCC, (char) (0x80 | ((c >> 0) & 0x3F)), retLen);
                }
            }
            //worst case scenario for character [0x7ff-] every single
            //character will be encoded into 9 characters.
            if (retLen + 9 > retCC.length) {
                int newLen = retCC.length * 2 + 16;
                if (newLen < 0) {
                    newLen = Integer.MAX_VALUE;
                }
                char[] buf = new char[newLen];
                System.arraycopy(retCC, 0, buf, 0, retLen);
                retCC = buf;
            }
        }
        return new String(retCC, 0, retLen);
    }

    private static int escape(char[] cc, char c, int index) {
        cc[index++] = '%';
        cc[index++] = Character.forDigit((c >> 4) & 0xF, 16);
        cc[index++] = Character.forDigit(c & 0xF, 16);
        return index;
    }

    private static int firstEncodeIndex(String path) {
        int len = path.length();
        for (int i = 0; i < len; i++) {
            char c = path.charAt(i);
            // Ordering in the following test is performance sensitive,
            // and typically paths have most chars in the a-z range, then
            // in the symbol range '&'-':' (includes '.', '/' and '0'-'9')
            // and more rarely in the A-Z range.
            if (c >= 'a' && c <= 'z' ||
                    c >= '&' && c <= ':' ||
                    c >= 'A' && c <= 'Z') {
                continue;
            } else if (c > 0x007F || match(c, L_ENCODED, H_ENCODED)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean match(char c, long lowMask, long highMask) {
        if (c < 64) {
            return ((1L << c) & lowMask) != 0;
        }
        if (c < 128) {
            return ((1L << (c - 64)) & highMask) != 0;
        }
        return false;
    }

    private static class JvmLoader extends ProxyClassLoader {

        private final ClassLoader classLoader = HomioClassLoader.class.getClassLoader();

        @Override
        public Class<?> loadClass(String className, boolean resolveIt) {
            try {
                return classLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @Override
        public InputStream loadResource(String name) {
            return classLoader.getResourceAsStream(name);
        }

        @Override
        public URL findResource(String name) {
            return classLoader.getResource(name);
        }
    }
}
