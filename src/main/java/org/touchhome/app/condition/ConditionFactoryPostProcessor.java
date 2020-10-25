package org.touchhome.app.condition;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.condition.ExecuteOnce;
import org.touchhome.bundle.api.condition.LinuxEnvironmentCondition;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

@Log4j2
@Component
@Conditional(LinuxEnvironmentCondition.class)
public class ConditionFactoryPostProcessor implements BeanPostProcessor {

    private final List<ExecuteContext> executeContexts = new ArrayList<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    @Autowired
    private ConditionHardwareRepository conditionHardwareRepository;

    private Boolean hasInternet = null;

    public ConditionFactoryPostProcessor() {
        // thread looking for state changed
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    lock.lock();
                    condition.await();
                    for (Iterator<ExecuteContext> iterator = executeContexts.iterator(); iterator.hasNext(); ) {
                        ExecuteContext executeContext = iterator.next();
                        if (executeContext.requireInternet()) {
                            if (executeContext.tryExecute()) {
                                iterator.remove();
                            }
                        }

                    }
                } catch (Exception ex) {
                    log.error("Unrecognized error while run conditional", ex);
                } finally {
                    lock.unlock();
                }
            }
        }).start();

        // listener for internet access
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                URLConnection connection = new URL("http://www.google.com").openConnection();
                connection.connect();
                updateInternetStatus(true);
            } catch (Exception ex) {
                updateInternetStatus(false);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().getName().startsWith("org.touchhome")) {
            for (Method method : MethodUtils.getMethodsWithAnnotation(bean.getClass(), ExecuteOnce.class)) {
                ExecuteContext executeContext = new ExecuteContext(method, bean);
                if (executeContext.requireRun()) {
                    executeContexts.add(executeContext);
                }
            }
        }
        return bean;
    }

    private void updateInternetStatus(boolean hasInternet) {
        if (this.hasInternet == null || this.hasInternet != hasInternet) {
            this.hasInternet = hasInternet;

            this.lock.lock();
            this.condition.signal();
            this.lock.unlock();
        }
    }

    private class ExecuteContext {
        private final ExecuteOnce executeOnce;
        private final Method method;
        private Object bean;

        private ExecuteContext(Method method, Object bean) {
            this.method = method;
            this.bean = bean;
            this.executeOnce = method.getDeclaredAnnotation(ExecuteOnce.class);
        }

        boolean tryExecute() throws InvocationTargetException, IllegalAccessException {
            if (requireInternet() && hasInternet) {
                method.invoke(bean);
                return true;
            }
            return false;
        }

        boolean requireRun() {
            return !assertSoftware(this.executeOnce.skipIfInstalled(), soft -> conditionHardwareRepository.isSoftwareInstalled(soft));
        }

        private boolean assertSoftware(String[] software, Predicate<String> predicate) {
            for (String soft : software) {
                if (!predicate.test(soft)) {
                    return false;
                }
            }
            return true;
        }

        boolean requireInternet() {
            return this.executeOnce.requireInternet();
        }
    }
}
