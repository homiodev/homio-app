package org.touchhome.app.extloader;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.util.CommonUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class BundleClassLoaderHolder extends ClassLoader {

    private Map<String, SingleBundleClassLoader> bundleJarClassLoaders = new HashMap<>();

    public void setClassLoaders(String bundleId, ClassLoader classLoader) {
        CommonUtils.addClassLoader(bundleId, classLoader);
        bundleJarClassLoaders.put(bundleId, (SingleBundleClassLoader) classLoader);
    }

    void destroy(String bundleName) {
        CommonUtils.removeClassLoader(bundleName);
        bundleJarClassLoaders.remove(bundleName).destroy();
    }

    SingleBundleClassLoader getBundleClassLoader(String bundleName) {
        return bundleJarClassLoaders.get(bundleName);
    }

    @Override
    public URL getResource(String name) {
        for (SingleBundleClassLoader loader : bundleJarClassLoaders.values()) {
            URL resource = loader.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    @Override
    protected URL findResource(String name) {
        return getResource(name);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (Map.Entry<String, SingleBundleClassLoader> entry : bundleJarClassLoaders.entrySet()) {
            try {
                Class loadClass = entry.getValue().loadClass(name, resolve);
                if (loadClass != null) {
                    return loadClass;
                }
            } catch (ClassNotFoundException ignore) {
            }
        }
        throw new ClassNotFoundException();
    }

    public List<ClassPathScanningCandidateComponentProvider> getResourceScanners(boolean includeInterfaces) {
        List<ClassPathScanningCandidateComponentProvider> scanners = new ArrayList<>();
        scanners.add(createClassPathScanningCandidateComponentProvider(includeInterfaces, null));

        for (SingleBundleClassLoader jarLoader : bundleJarClassLoaders.values()) {
            scanners.add(createClassPathScanningCandidateComponentProvider(includeInterfaces, jarLoader));
        }
        return scanners;
    }

    private ClassPathScanningCandidateComponentProvider createClassPathScanningCandidateComponentProvider(boolean includeInterfaces, SingleBundleClassLoader jarLoader) {
        ClassPathScanningCandidateComponentProvider provider = !includeInterfaces ? new ClassPathScanningCandidateComponentProvider(false) :
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return true;
                    }
                };
        if (jarLoader != null) {
            provider.setResourceLoader(new PathMatchingResourcePatternResolver(jarLoader));
        }
        return provider;
    }
}
