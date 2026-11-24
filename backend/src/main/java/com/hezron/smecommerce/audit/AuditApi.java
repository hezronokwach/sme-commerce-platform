package com.hezron.smecommerce.audit;

import com.hezron.smecommerce.shared.TenantId;
import java.util.Map;
import java.util.UUID;

/** Records a sensitive action. Any module may call it. */
public interface AuditApi {

    /**
     * Saves one audit row inside the caller's transaction: if the change is rolled back, so is its record.
     * Who did it and from where are read from the current request.
     *
     * @param action  for example PRODUCT_PRICE_CHANGED
     * @param details before and after values. Never put secrets or full phone numbers here.
     */
    void record(TenantId tenantId, String action, String entityType, UUID entityId, Map<String, Object> details);

}
