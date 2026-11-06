package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.identity.IdentityApi;
import com.hezron.smecommerce.identity.internal.persistence.CustomerEntity;
import com.hezron.smecommerce.identity.internal.persistence.CustomerRepository;
import com.hezron.smecommerce.shared.PhoneNumber;
import com.hezron.smecommerce.shared.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A shop's customers. The only implementation of IdentityApi; package-private, so others see the interface. */
@Service
class CustomerService implements IdentityApi {

    private final CustomerRepository customers;

    CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional
    public UUID findOrCreateCustomer(TenantId tenantId, String fullName, PhoneNumber phone) {
        String name = fullName.strip();

        // 1. Add the customer if this shop doesn't have this phone yet (does nothing if it does).
        customers.insertIfMissing(UUID.randomUUID(), tenantId.value(), phone.value(), name, Instant.now());

        // 2. Now the row is certainly there: read it.
        Optional<CustomerEntity> found = customers.findByTenantIdAndPhone(tenantId.value(), phone.value());
        if (found.isEmpty()) {
            // Can't happen: the insert above or an earlier one created it, in this shop.
            throw new IllegalStateException("Customer missing right after insert");
        }
        CustomerEntity customer = found.get();

        // 3. A returning customer who typed a different name: keep the newest one.
        if (!customer.getFullName().equals(name)) {
            customer.changeName(name);
        }
        return customer.getId();
    }

}
