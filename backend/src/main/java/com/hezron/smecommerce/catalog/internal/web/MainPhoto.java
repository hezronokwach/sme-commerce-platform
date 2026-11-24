package com.hezron.smecommerce.catalog.internal.web;

import java.util.List;

/** The main photo of a product is the first of its photos. Lists and cards show only this one. */
final class MainPhoto {

    private MainPhoto() {
    }

    /** The first address, or null when there are no photos. */
    static String of(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        return imageUrls.get(0);
    }

}
