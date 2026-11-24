package com.hezron.smecommerce.catalog.internal.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Cloudinary account photos are uploaded to (ADR 0022). All three keys come from the environment;
 * when any is empty, photo uploads are "not set up" and the admin says so.
 *
 * @param cloudName the account's name, part of every photo address
 * @param apiKey    public; sent to the browser with each signature
 * @param apiSecret secret; only used here to sign, never sent anywhere or logged
 * @param folder    every shop's photos go in folder/shop-id
 */
@ConfigurationProperties("app.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret, String folder) {

    public boolean isConfigured() {
        return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

}
