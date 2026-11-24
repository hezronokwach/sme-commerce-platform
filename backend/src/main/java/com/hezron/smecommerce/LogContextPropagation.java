package com.hezron.smecommerce;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.context.annotation.Configuration;

/**
 * Carries the log context (correlation id, shop) onto background threads, so event listeners and the
 * fake provider's callback keep the id of the request that started them.
 * <p>
 * Spring Modulith already wraps the task executor in a ContextPropagatingTaskDecorator, which copies
 * every value registered with Micrometer's ContextRegistry to the new thread and clears it afterwards.
 * Registering our two log keys here is all that's needed.
 */
@Configuration(proxyBeanMethods = false)
class LogContextPropagation {

    LogContextPropagation() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                new Slf4jThreadLocalAccessor(CorrelationIdFilter.MDC_KEY, LogContextInterceptor.MDC_KEY));
    }

}
