package org.touchhome.app.extloader;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.xeustechnologies.jcl.ClasspathResources;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.ProxyClassLoader;
import sun.net.www.ParseUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

@Log4j2
public class BundleClassLoader extends JarClassLoader {

    BundleClassLoader(Path jarPath) {
        String jarFileName = jarPath.getFileName().toString();
        log.info("Adding jar <{}> to classpath", jarFileName);
        InternalJarClassLoader internalJarClassLoader = new InternalJarClassLoader(jarPath);
        super.addLoader(internalJarClassLoader);
    }

    @Override
    public URL getResource(String name) {
        return super.getResource(name);
    }

    @Override
    protected URL findResource(String name) {
        return super.findResource(name);
    }

    public class InternalJarClassLoader extends ProxyClassLoader {
        private final JarFile jarFile;
        private final ClasspathResources classpathResources = new ClasspathResources();
        final Map<String, Class> classes = new HashMap<>();
        private final URL baseUrl;

        @SneakyThrows
        InternalJarClassLoader(Path jarPath) {
            this.classpathResources.loadResource(jarPath.toString());
            this.jarFile = new JarFile(jarPath.toFile());
            this.baseUrl = new URL("jar", "",  jarPath.toUri().toURL().toString() + "!/");
        }

        @Override
        public Class loadClass(String className, boolean resolveIt) {
            if (!classes.containsKey(className)) {
                byte[] classBytes = classpathResources.getResource(formatClassName(className));
                if (classBytes != null) {
                    Class result = defineClass(className, classBytes, 0, classBytes.length);

                    if (result != null) {
                        if (result.getPackage() == null) {
                            int lastDotIndex = className.lastIndexOf('.');
                            String packageName = (lastDotIndex >= 0) ? className.substring(0, lastDotIndex) : "";
                            definePackage(packageName, null, null, null, null, null, null, null);
                        }
                        if (resolveIt) {
                            resolveClass(result);
                        }
                        classes.put(className, result);
                    }
                }
            }
            return classes.get(className);
        }

        @Override
        public InputStream loadResource(String name) {
            byte[] arr = classpathResources.getResource(name);
            if (arr != null) {
                return new ByteArrayInputStream(arr);
            }

            return null;
        }

        @Override
        public URL findResource(String name) {
            URL url = classpathResources.getResourceURL(name);
            if (url != null) {
                return url;
            }
            try {
                if (this.jarFile.getEntry(name) != null) {
                    return new URL(this.baseUrl, ParseUtil.encodePath(name, false));
                }
            } catch (Exception ignore) {
            }
            return null;
        }
    }
}
