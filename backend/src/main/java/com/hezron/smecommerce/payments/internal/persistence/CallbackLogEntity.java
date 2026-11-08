package com.hezron.smecommerce.payments.internal.persistence;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.domain.CallbackOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/** Row in {@code payments.callback_log}: one callback exactly as it arrived, and what we did with it. */
@Entity
@Table(schema = "payments", name = "callback_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CallbackLogEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentProviderName provider;

    @Column(name = "provider_reference", updatable = false)
    private String providerReference;

    /** The JSON body, untouched. JdbcTypeCode(JSON) tells Hibernate the column is jsonb, not text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_body", nullable = false, updatable = false)
    private String rawBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CallbackOutcome outcome;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static CallbackLogEntity newEntry(PaymentProviderName provider, String providerReference,
            String rawBody, CallbackOutcome outcome) {
        CallbackLogEntity entry = new CallbackLogEntity();
        entry.id = UUID.randomUUID();
        entry.provider = provider;
        entry.providerReference = providerReference;
        entry.rawBody = rawBody;
        entry.outcome = outcome;
        entry.receivedAt = Instant.now();
        return entry;
    }

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

}
