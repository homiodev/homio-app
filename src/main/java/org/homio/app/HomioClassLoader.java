package org.homio.app;

import lombok.RequiredArgsConstructor;
import org.homio.app.extloader.AddonClassLoader;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.net.URL;
import java.util.*;

public class HomioClassLoader extends ClassLoader {

    private static final Map<String, AddonClassLoader> addonJarClassLoaders = new HashMap<>();

    public static final HomioClassLoader INSTANCE = new HomioClassLoader(HomioClassLoader.class.getClassLoader());

    private HomioClassLoader(ClassLoader parent) {
        super(parent);
    }

    public static void addClassLoaders(String addonID, ClassLoader classLoader) {
        addonJarClassLoaders.put(addonID, (AddonClassLoader) classLoader);
    }

    public static void removeClassLoader(String addonID) {
        addonJarClassLoaders.remove(addonID).destroy();
    }

    public static AddonClassLoader getAddonClassLoader(String addonID) {
        return addonJarClassLoaders.get(addonID);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (Map.Entry<String, AddonClassLoader> entry : addonJarClassLoaders.entrySet()) {
            try {
                Class<?> loadClass = entry.getValue().loadClass(name);
                if (loadClass != null) {
                    return loadClass;
                }
            } catch (ClassNotFoundException ignore) {
            }
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        List<Enumeration<URL>> resourceList = new ArrayList<>();
        for (AddonClassLoader loader : addonJarClassLoaders.values()) {
            resourceList.add(loader.getResources(name));
        }
        return new SeqEn<>(Collections.enumeration(resourceList));
    }

    @Override
    protected URL findResource(String name) {
        for (AddonClassLoader loader : addonJarClassLoaders.values()) {
            URL resource = loader.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    public static ClassPathScanningCandidateComponentProvider getResourceScanner(boolean includeInterfaces) {
        return getResourceScanner(includeInterfaces, null);
    }

    public static ClassPathScanningCandidateComponentProvider getResourceScanner(
            boolean includeInterfaces, @Nullable ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider provider =
                !includeInterfaces ? new ClassPathScanningCandidateComponentProvider(false) :
                        new ClassPathScanningCandidateComponentProvider(false) {
                            @Override
                            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                                return true;
                            }
                        };
        if (classLoader != null) {
            provider.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));
        }
        return provider;
    }

    @RequiredArgsConstructor
    static class SeqEn<T> implements Enumeration<T> {

        private final Enumeration<? extends Enumeration<? extends T>> en;

        private Enumeration<? extends T> current;

        private boolean checked = false;

        public boolean hasMoreElements() {
            if (!checked) {
                ensureCurrent();
                checked = true;
            }

            return current != null;
        }

        public T nextElement() {
            if (!checked) {
                ensureCurrent();
            }

            if (current != null) {
                checked = false;

                return current.nextElement();
            } else {
                checked = true;
                throw new java.util.NoSuchElementException();
            }
        }

        private void ensureCurrent() {
            while ((current == null) || !current.hasMoreElements()) {
                if (en.hasMoreElements()) {
                    current = en.nextElement();
                } else {
                    // no next valid enumeration
                    current = null;
                    return;
                }
            }
        }
    }
}
