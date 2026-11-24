package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.PhotoUploadSignature;
import com.hezron.smecommerce.catalog.internal.application.PhotoUploadSigner;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/admin/photo-uploads/signature}: permission for one photo upload to Cloudinary, for
 * product photos and the shop's logo. Owners and staff may both ask, since both manage products.
 */
@RestController
@RequestMapping("/api/admin/photo-uploads")
class PhotoUploadController {

    private final PhotoUploadSigner signer;

    PhotoUploadController(PhotoUploadSigner signer) {
        this.signer = signer;
    }

    @PostMapping("/signature")
    PhotoUploadSignatureResponse sign(@AuthenticationPrincipal Jwt jwt) {
        PhotoUploadSignature signature = signer.signFor(CallerTenant.idFrom(jwt));
        return PhotoUploadSignatureResponse.fromSignature(signature);
    }

}
