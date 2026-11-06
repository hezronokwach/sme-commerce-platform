package com.hezron.smecommerce.identity;

import com.hezron.smecommerce.shared.PhoneNumber;
import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * What other modules may ask of identity. Ordering is the only caller so far. Implemented in
 * identity.internal; callers only see this interface.
 */
public interface IdentityApi {

    /**
     * The shop's customer with this phone number, created if this is their first order (Guide 2.6,
     * workflow W9). A guest needs no account: the phone number is what identifies them within a shop.
     * If they typed a different name this time, the name is updated.
     * <p>
     * Joins the caller's transaction, so if the order fails, a newly created customer is rolled back too.
     *
     * @return the customer's id
     */
    UUID findOrCreateCustomer(TenantId tenantId, String fullName, PhoneNumber phone);

}
