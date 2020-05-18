package org.touchhome.bundle.api.util;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ClassFinder {
    public static final String CLASSES_WITH_PARENT_CLASS = "CLASSES_WITH_PARENT_CLASS";
    public static final String REPOSITORY_BY_CLAZZ = "REPOSITORY_BY_CLAZZ";

    @SneakyThrows
    public static <T> List<T> createClassesWithParent(Class<T> parentClass, ClassFinder classFinder) {
        List<T> list = new ArrayList<>();
        for (Class<? extends T> clazz : classFinder.getClassesWithParent(parentClass)) {
            list.add(clazz.getConstructor().newInstance());
        }
        return list;
    }

    public static <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation, boolean includeInterfaces) {
        ClassPathScanningCandidateComponentProvider scanner = !includeInterfaces ? new ClassPathScanningCandidateComponentProvider(false) :
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return true;
                    }
                };

        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        List<Class<? extends T>> findedClasses = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("org.touchhome")) {
            try {
                findedClasses.add((Class<? extends T>) Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return findedClasses;
    }

    public <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation) {
        return getClassesWithAnnotation(annotation, true);
    }

    /**
     * NOT call this method from this class
     */
    @Cacheable(CLASSES_WITH_PARENT_CLASS)
    public <T> List<Class<? extends T>> getClassesWithParent(Class<T> parentClass) {
        return getClassesWithParent(parentClass, null);
    }

    public <T> List<Class<? extends T>> getClassesWithParent(Class<T> parentClass, String className) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AssignableTypeFilter(parentClass));
        List<Class<? extends T>> findedClasses = new ArrayList<>();

        getClassesWithParentFromPackage("org.touchhome", className, scanner, findedClasses);
        if (findedClasses.isEmpty()) {
            getClassesWithParentFromPackage("com.pi4j", className, scanner, findedClasses);
        }

        return findedClasses;
    }

    @Cacheable(REPOSITORY_BY_CLAZZ)
    public <T extends BaseEntity, R extends AbstractRepository<T>> R getRepositoryByClass(Class<T> clazz) {
        List<R> potentialRepository = new ArrayList<>();

        Map<String, AbstractRepository> repositories = ApplicationContextHolder.getBean("repositories", Map.class);
        for (AbstractRepository abstractRepository : repositories.values()) {
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

    private <T> void getClassesWithParentFromPackage(String packageName, String className, ClassPathScanningCandidateComponentProvider scanner, List<Class<? extends T>> findedClasses) {
        try {
            for (BeanDefinition bd : scanner.findCandidateComponents(packageName)) {
                if (className == null || bd.getBeanClassName().endsWith("." + className)) {
                    try {
                        findedClasses.add((Class<? extends T>) Class.forName(bd.getBeanClassName()));
                    } catch (ClassNotFoundException ignore) {
                    }
                }
            }
        } catch (Exception ex) {
            System.out.printf("ex.get");
        }
    }
}
