package com.hezron.smecommerce;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Switches on LogContextInterceptor for every request. */
@Configuration(proxyBeanMethods = false)
class WebLoggingConfig implements WebMvcConfigurer {

    private final LogContextInterceptor logContext;

    WebLoggingConfig(LogContextInterceptor logContext) {
        this.logContext = logContext;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logContext);
    }

}
