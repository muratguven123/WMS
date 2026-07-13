package com.wms.localization.listener;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Hibernate entity listener'ları gibi Spring tarafından yönetilmeyen nesnelerin
 * ApplicationContext'e erişmesini sağlayan singleton köprü.
 *
 * <p>Bu bean uygulama başladığında Spring tarafından initialize edilir ve
 * static referans üzerinden context paylaşımı yapar. JPA entity listener'larında
 * {@code @Autowired} çalışmadığından bu yaklaşım tercih edilir.</p>
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.context = applicationContext;
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            throw new IllegalStateException(
                    "SpringContextHolder not initialized — ApplicationContext is null");
        }
        return context.getBean(beanClass);
    }
}
