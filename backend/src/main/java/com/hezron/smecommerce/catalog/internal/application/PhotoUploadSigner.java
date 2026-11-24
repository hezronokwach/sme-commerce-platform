package com.hezron.smecommerce.catalog.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Signs photo uploads (ADR 0022). The photo goes from the owner's browser straight to Cloudinary, so it
 * never passes through our server; the signature proves we allowed that upload, into that shop's folder,
 * at that time. Cloudinary refuses a signature older than an hour.
 */
@Service
@EnableConfigurationProperties(CloudinaryProperties.class)
public class PhotoUploadSigner {

    private final CloudinaryProperties cloudinary;

    PhotoUploadSigner(CloudinaryProperties cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** @throws PhotoUploadsNotSetUpException if the Cloudinary keys aren't set */
    public PhotoUploadSignature signFor(UUID tenantId) {
        if (!cloudinary.isConfigured()) {
            throw new PhotoUploadsNotSetUpException();
        }
        long timestamp = Instant.now().getEpochSecond();
        String folder = cloudinary.folder() + "/" + tenantId;

        // Cloudinary's rule: the signed parameters sorted by name, as name=value joined with "&".
        String toSign = "folder=" + folder + "&timestamp=" + timestamp;
        String signature = signatureOf(toSign, cloudinary.apiSecret());

        String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudinary.cloudName() + "/image/upload";
        return new PhotoUploadSignature(uploadUrl, cloudinary.apiKey(), timestamp, folder, signature);
    }

    /** SHA-1 of the parameters followed by the secret, in lowercase hex. */
    static String signatureOf(String parameters, String apiSecret) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((parameters + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must have SHA-1.
            throw new IllegalStateException(e);
        }
    }

}
