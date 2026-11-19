package com.hezron.smecommerce.payments.internal.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How payments protects itself from a provider that is slow or down (ADR 0014).
 *
 * @param failuresToOpen   this many failed calls in a row open the provider's circuit breaker
 * @param openFor          how long nobody calls a provider whose breaker opened
 * @param queryRetryWaits  a status query (a read, so safe to repeat) is tried again after each of these
 *                         waits: 200 ms then 1 s means up to 3 tries in all
 */
@ConfigurationProperties("app.payments.provider-calls")
public record ProviderCallProperties(int failuresToOpen, Duration openFor, List<Duration> queryRetryWaits) {

    public ProviderCallProperties {
        if (failuresToOpen <= 0) {
            failuresToOpen = 5;
        }
        if (openFor == null) {
            openFor = Duration.ofSeconds(30);
        }
        if (queryRetryWaits == null) {
            queryRetryWaits = List.of(Duration.ofMillis(200), Duration.ofSeconds(1));
        }
    }

}
