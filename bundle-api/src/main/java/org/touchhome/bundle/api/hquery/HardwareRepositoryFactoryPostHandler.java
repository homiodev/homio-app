package org.touchhome.bundle.api.hquery;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public interface HardwareRepositoryFactoryPostHandler {
    void accept(ConfigurableListableBeanFactory beanFactory);
}
