package com.hezron.smecommerce.payments.internal.persistence;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.domain.IllegalPaymentTransitionException;
import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/** Row in {@code payments.payments}: one attempt to collect the money for one order. */
@Entity
@Table(schema = "payments", name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class PaymentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentProviderName provider;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false)
    private String currency;

    /** 2547XXXXXXXX. Never write it to the logs in full. */
    @Column(name = "payer_phone", nullable = false, updatable = false)
    private String payerPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "next_check_at")
    private Instant nextCheckAt;

    /** Optimistic locking. Never set in Java: Hibernate owns this number. */
    @SuppressWarnings("unused")
    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    /** A new attempt, not yet sent to the provider. */
    public static PaymentEntity newPayment(UUID tenantId, UUID orderId, PaymentProviderName provider,
            long amountMinor, String currency, String payerPhone) {
        Instant now = Instant.now();
        PaymentEntity payment = new PaymentEntity();
        payment.id = UUID.randomUUID();
        payment.tenantId = tenantId;
        payment.orderId = orderId;
        payment.provider = provider;
        payment.amountMinor = amountMinor;
        payment.currency = currency;
        payment.payerPhone = payerPhone;
        payment.status = PaymentStatus.INITIATED;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    // ---------------------------------------------------------------------------------------------
    // The state machine. These methods are the ONLY way a payment's status changes.
    //
    //   markPending   INITIATED or UNKNOWN             -> PENDING    (provider accepted)
    //   markUnknown   INITIATED                        -> UNKNOWN    (no clear answer from the provider)
    //   succeed       PENDING or UNKNOWN               -> SUCCEEDED  (money arrived)
    //   fail          INITIATED, PENDING or UNKNOWN    -> FAILED     (provider said no)
    //
    // SUCCEEDED and FAILED are final.
    // ---------------------------------------------------------------------------------------------

    /** The provider accepted the request and gave us its reference; a callback will follow. */
    public void markPending(String providerReference, Instant nextCheckAt) {
        if (status != PaymentStatus.INITIATED && status != PaymentStatus.UNKNOWN) {
            throw new IllegalPaymentTransitionException(status, PaymentStatus.PENDING);
        }
        this.status = PaymentStatus.PENDING;
        this.providerReference = providerReference;
        this.nextCheckAt = nextCheckAt;
    }

    /** The request may or may not have reached the provider; a status check must find out. */
    public void markUnknown(Instant nextCheckAt) {
        if (status != PaymentStatus.INITIATED) {
            throw new IllegalPaymentTransitionException(status, PaymentStatus.UNKNOWN);
        }
        this.status = PaymentStatus.UNKNOWN;
        this.nextCheckAt = nextCheckAt;
    }

    public void succeed(String receiptNumber, String resultCode) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.UNKNOWN) {
            throw new IllegalPaymentTransitionException(status, PaymentStatus.SUCCEEDED);
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.receiptNumber = receiptNumber;
        this.resultCode = resultCode;
        this.nextCheckAt = null;
        this.completedAt = Instant.now();
    }

    public void fail(String failureReason, String resultCode) {
        if (status.isFinal() || status == PaymentStatus.TIMED_OUT) {
            throw new IllegalPaymentTransitionException(status, PaymentStatus.FAILED);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
        this.resultCode = resultCode;
        this.nextCheckAt = null;
        this.completedAt = Instant.now();
    }

    /** Written out only to say it's never null: every payment gets its id in newPayment. */
    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

}
