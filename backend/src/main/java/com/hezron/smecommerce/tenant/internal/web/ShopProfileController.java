package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.tenant.internal.application.ShopProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public storefront endpoint: no login, the security chain leaves {@code /api/store/**} open.
 * The shop comes from the web name in the URL, the same way every storefront endpoint will find it.
 */
@RestController
@RequestMapping("/api/store")
class ShopProfileController {

    private final ShopProfileService shopProfileService;

    ShopProfileController(ShopProfileService shopProfileService) {
        this.shopProfileService = shopProfileService;
    }

    @GetMapping("/{slug}")
    ShopProfileResponse getProfile(@PathVariable String slug) {
        return ShopProfileResponse.fromResult(shopProfileService.getProfile(slug));
    }

}
