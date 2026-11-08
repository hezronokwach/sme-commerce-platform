package com.hezron.smecommerce.payments.internal.persistence;

import com.hezron.smecommerce.payments.PaymentProviderName;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** No tenant here: the log is written before we know the shop (see the migration). Only payments reads it. */
public interface CallbackLogRepository extends JpaRepository<CallbackLogEntity, UUID> {

    List<CallbackLogEntity> findAllByProviderAndProviderReferenceOrderByReceivedAtAsc(PaymentProviderName provider,
            String providerReference);

}
