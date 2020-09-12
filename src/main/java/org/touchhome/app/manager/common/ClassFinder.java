package org.touchhome.app.manager.common;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.touchhome.app.extloader.BundleClassLoaderHolder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClassFinder {
    public static final String CLASSES_WITH_PARENT_CLASS = "CLASSES_WITH_PARENT_CLASS";
    public static final String REPOSITORY_BY_CLAZZ = "REPOSITORY_BY_CLAZZ";
    private final BundleClassLoaderHolder bundleClassLoaderHolder;
    private final ApplicationContext applicationContext;

    @SneakyThrows
    public static <T> List<T> createClassesWithParent(Class<T> parentClass, ClassFinder classFinder) {
        List<T> list = new ArrayList<>();
        for (Class<? extends T> clazz : classFinder.getClassesWithParent(parentClass)) {
            list.add(clazz.getConstructor().newInstance());
        }
        return list;
    }

    private <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation, boolean includeInterfaces) {
        List<Class<? extends T>> foundClasses = new ArrayList<>();
        for (ClassPathScanningCandidateComponentProvider scanner : bundleClassLoaderHolder.getResourceScanners(includeInterfaces)) {
            scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
            for (BeanDefinition bd : scanner.findCandidateComponents("org.touchhome")) {
                try {
                    foundClasses.add((Class<? extends T>) scanner.getResourceLoader().getClassLoader().loadClass(bd.getBeanClassName()));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return foundClasses;
    }

    public <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation) {
        return getClassesWithAnnotation(annotation, true);
    }

    /**
     * NOT call this method from this class
     */
    @Cacheable(CLASSES_WITH_PARENT_CLASS)
    public <T> List<Class<? extends T>> getClassesWithParent(Class<T> parentClass) {
        return getClassesWithParent(parentClass, null, null);
    }

    public <T> List<Class<? extends T>> getClassesWithParent(Class<T> parentClass, String className, String basePackage) {
        if (basePackage == null) {
            basePackage = "org.touchhome";
        }
        List<Class<? extends T>> foundClasses = new ArrayList<>();
        for (ClassPathScanningCandidateComponentProvider scanner : bundleClassLoaderHolder.getResourceScanners(false)) {
            scanner.addIncludeFilter(new AssignableTypeFilter(parentClass));

            getClassesWithParentFromPackage(basePackage, className, scanner, foundClasses);
            // TODO: refactor
            if (foundClasses.isEmpty()) {
                getClassesWithParentFromPackage("com.pi4j", className, scanner, foundClasses);
            }
        }

        return foundClasses;
    }

    @Cacheable(REPOSITORY_BY_CLAZZ)
    public <T extends BaseEntity, R extends AbstractRepository<T>> R getRepositoryByClass(Class<T> clazz) {
        List<R> potentialRepository = new ArrayList<>();

        for (AbstractRepository abstractRepository : applicationContext.getBean(EntityContext.class).getRepositories()) {
            if (abstractRepository.getEntityClass().equals(clazz)) {
                return (R) abstractRepository;
            }
            if (abstractRepository.getEntityClass().isAssignableFrom(clazz)) {
                potentialRepository.add((R) abstractRepository);
            }
        }
        if (!potentialRepository.isEmpty()) {
            if (potentialRepository.size() == 1) {
                return potentialRepository.get(0);
            }
            // find most child repository
            R bestPotentialRepository = null;
            for (R r : potentialRepository) {
                Class entityClass = clazz;
                // get level
                int lowestLevel = -1;
                int level = Integer.MAX_VALUE;
                while (entityClass != null) {
                    if (entityClass.equals(r.getEntityClass())) {
                        if (level < lowestLevel) {
                            bestPotentialRepository = r;
                            break;
                        }
                    } else {
                        level++;
                        entityClass = entityClass.getSuperclass();
                    }
                }
            }
            if (bestPotentialRepository != null) {
                return bestPotentialRepository;
            }
        }

        throw new IllegalStateException("Unable find repository for entity class: " + clazz);
    }

    private <T> void getClassesWithParentFromPackage(String basePackage, String className, ClassPathScanningCandidateComponentProvider scanner, List<Class<? extends T>> foundClasses) {
        try {
            for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
                if (className == null || bd.getBeanClassName().endsWith("." + className)) {
                    try {
                        foundClasses.add((Class<? extends T>) scanner.getResourceLoader().getClassLoader().loadClass(bd.getBeanClassName()));
                    } catch (ClassNotFoundException ignore) {
                    }
                }
            }
        } catch (Exception ignore) {
        }
    }
}
