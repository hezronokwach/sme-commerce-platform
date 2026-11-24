package com.hezron.smecommerce.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The upload signature, checked without Spring or Cloudinary. */
class PhotoUploadSignerTests {

    @Test
    void signsTheWayCloudinaryDocumentsIt() {
        // The example from Cloudinary's "Generating authentication signatures" page.
        String parameters = "eager=w_400,h_300,c_pad|w_260,h_200,c_crop&public_id=sample_image&timestamp=1315060510";

        assertThat(PhotoUploadSigner.signatureOf(parameters, "abcd"))
                .isEqualTo("bfd09f95f331f558cbd1320e67aa8d488770583e");
    }

    @Test
    void signsAnUploadIntoTheShopsOwnFolder() {
        PhotoUploadSigner signer = new PhotoUploadSigner(
                new CloudinaryProperties("demo-cloud", "public-key", "test-secret", "sme-commerce"));
        UUID shop = UUID.randomUUID();

        PhotoUploadSignature signature = signer.signFor(shop);

        assertThat(signature.folder()).isEqualTo("sme-commerce/" + shop);
        assertThat(signature.apiKey()).isEqualTo("public-key");
        assertThat(signature.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/demo-cloud/image/upload");
        String expected = PhotoUploadSigner.signatureOf(
                "folder=sme-commerce/" + shop + "&timestamp=" + signature.timestamp(), "test-secret");
        assertThat(signature.signature()).isEqualTo(expected);
    }

    @Test
    void refusesWhenAKeyIsMissing() {
        PhotoUploadSigner signer = new PhotoUploadSigner(new CloudinaryProperties("demo-cloud", "public-key", "", "x"));

        assertThatThrownBy(() -> signer.signFor(UUID.randomUUID())).isInstanceOf(PhotoUploadsNotSetUpException.class);
    }

}
