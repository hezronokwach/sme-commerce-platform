package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.PhotoUploadSignature;

/**
 * JSON answer of {@code POST /api/admin/photo-uploads/signature}. The browser posts the file to
 * {@code uploadUrl} as form data with {@code api_key}, {@code timestamp}, {@code folder} and
 * {@code signature}; Cloudinary answers with the photo's {@code secure_url}.
 */
record PhotoUploadSignatureResponse(String uploadUrl, String apiKey, long timestamp, String folder, String signature) {

    static PhotoUploadSignatureResponse fromSignature(PhotoUploadSignature signature) {
        return new PhotoUploadSignatureResponse(signature.uploadUrl(), signature.apiKey(), signature.timestamp(),
                signature.folder(), signature.signature());
    }

}
