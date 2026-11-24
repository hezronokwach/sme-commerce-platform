package com.hezron.smecommerce.catalog.internal.application;

/**
 * Everything the browser needs for one signed upload straight to Cloudinary. The browser sends the
 * file with apiKey, timestamp, folder and signature to uploadUrl; Cloudinary checks the signature and
 * refuses the upload if any of them was changed.
 */
public record PhotoUploadSignature(String uploadUrl, String apiKey, long timestamp, String folder, String signature) {
}
