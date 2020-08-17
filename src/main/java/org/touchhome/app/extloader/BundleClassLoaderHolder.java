package org.touchhome.app.extloader;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class BundleClassLoaderHolder extends ClassLoader {

    private Map<String, SingleBundleClassLoader> bundleJarClassLoaders = new HashMap<>();

    void addJar(String bundleName, Path jarPath) {
        String jarFileName = jarPath.getFileName().toString();
        log.info("Adding jar <{}> to classpath", jarFileName);
        SingleBundleClassLoader singleBundleCLassLoader = new SingleBundleClassLoader(jarPath);
        TouchHomeUtils.addClassLoader(bundleName, singleBundleCLassLoader);
        bundleJarClassLoaders.put(bundleName, singleBundleCLassLoader);
        // super.addLoader(internalJarClassLoader);
    }

    void destroy(String bundleName) {
        TouchHomeUtils.removeClassLoader(bundleName);
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
        for (SingleBundleClassLoader loader : bundleJarClassLoaders.values()) {
            Class loadClass = loader.loadClass(name, resolve);
            if (loadClass != null) {
                return loadClass;
            }
        }
        return null;
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
