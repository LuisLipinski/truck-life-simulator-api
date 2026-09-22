package com.luislipinski.trucklife.subscription.application;

import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import com.luislipinski.trucklife.subscription.persistence.PaymentOrderEntity;
import com.luislipinski.trucklife.subscription.persistence.PaymentOrderRepository;
import com.luislipinski.trucklife.subscription.persistence.PlanEntity;
import com.luislipinski.trucklife.subscription.persistence.PlanRepository;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionEntity;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionOwnerLock;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentCheckoutService implements PaymentOperations {

    private static final List<SubscriptionStatus> OPEN_SUBSCRIPTION_STATUSES =
            List.copyOf(EnumSet.of(
                    SubscriptionStatus.PENDING_PAYMENT,
                    SubscriptionStatus.ACTIVE,
                    SubscriptionStatus.PAST_DUE
            ));

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final SubscriptionOwnerLock ownerLock;
    private final PaymentProvider paymentProvider;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PaymentCheckoutService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            PaymentOrderRepository paymentOrderRepository,
            SubscriptionOwnerLock ownerLock,
            PaymentProvider paymentProvider,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.ownerLock = ownerLock;
        this.paymentProvider = paymentProvider;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public CheckoutResult checkoutPremium(UUID userId, String payerEmail, UUID operationId) {
        PlanEntity premium = premiumPlan();
        int priceCents = configuredPrice(premium);
        requireProviderConfigured();

        PreparedCheckout prepared = transactions.execute(status ->
                prepare(userId, operationId, premium, priceCents)
        );
        if (prepared == null) {
            throw new IllegalStateException("Checkout transaction returned no result");
        }
        if (prepared.replay()) {
            return new CheckoutResult(prepared.order(), true);
        }

        PaymentProvider.PixCheckout pix;
        try {
            pix = paymentProvider.createPix(new PaymentProvider.PixRequest(
                    operationId,
                    prepared.order().getId(),
                    payerEmail,
                    priceCents,
                    premium.getCurrency()
            ));
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> paymentOrderRepository
                    .findByIdAndUserId(prepared.order().getId(), userId)
                    .ifPresent(order -> order.markFailed(clock.instant())));
            throw new ApiProblemException(
                    HttpStatus.BAD_GATEWAY,
                    "PAYMENT_PROVIDER_ERROR",
                    "Payment provider error",
                    "The PIX provider could not create the charge"
            );
        }

        PaymentOrderEntity completed = transactions.execute(status -> {
            PaymentOrderEntity order = paymentOrderRepository
                    .findByIdAndUserId(prepared.order().getId(), userId)
                    .orElseThrow(() -> new IllegalStateException("Prepared payment order disappeared"));
            Instant now = clock.instant();
            order.markPending(
                    requiredProviderValue(pix.providerOrderId(), "providerOrderId"),
                    pix.providerPaymentId(),
                    requiredProviderValue(pix.pixCopyPaste(), "pixCopyPaste"),
                    requiredProviderValue(pix.pixQrCodeBase64(), "pixQrCodeBase64"),
                    pix.pixTicketUrl(),
                    pix.expiresAt(),
                    now
            );
            return paymentOrderRepository.saveAndFlush(order);
        });
        if (completed == null) {
            throw new IllegalStateException("Payment order update transaction returned no result");
        }
        return new CheckoutResult(completed, false);
    }

    @Override
    public PaymentOrderEntity payment(UUID userId, UUID paymentOrderId) {
        return paymentOrderRepository.findByIdAndUserId(paymentOrderId, userId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND",
                        "Payment not found",
                        "The requested payment does not exist"
                ));
    }

    @Override
    public List<PaymentOrderEntity> payments(UUID userId) {
        return paymentOrderRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Override
    public SubscriptionEntity currentSubscription(UUID userId) {
        return subscriptionRepository
                .findFirstByUserIdOrderByUpdatedAtDescIdDesc(userId)
                .orElse(null);
    }

    private PreparedCheckout prepare(
            UUID userId,
            UUID operationId,
            PlanEntity premium,
            int priceCents
    ) {
        ownerLock.lock(userId);

        PaymentOrderEntity existing = paymentOrderRepository
                .findByUserIdAndCheckoutOperationId(userId, operationId)
                .orElse(null);
        if (existing != null) {
            return new PreparedCheckout(existing, true);
        }

        Instant now = clock.instant();
        SubscriptionEntity subscription = subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByUpdatedAtDescIdDesc(userId, OPEN_SUBSCRIPTION_STATUSES)
                .orElse(null);

        if (subscription != null && !subscription.getPlanId().equals(premium.getId())) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "SUBSCRIPTION_STATE_CONFLICT",
                    "Subscription state conflict",
                    "An open subscription exists for another plan"
            );
        }

        if (subscription == null) {
            subscription = subscriptionRepository.saveAndFlush(new SubscriptionEntity(
                    UUID.randomUUID(),
                    userId,
                    premium.getId(),
                    SubscriptionStatus.PENDING_PAYMENT,
                    null,
                    null,
                    null,
                    false,
                    null,
                    now,
                    now
            ));
        }

        PaymentOrderEntity order = paymentOrderRepository.saveAndFlush(new PaymentOrderEntity(
                UUID.randomUUID(),
                userId,
                subscription.getId(),
                operationId,
                PaymentProviderCode.MERCADO_PAGO,
                priceCents,
                premium.getCurrency(),
                now
        ));
        return new PreparedCheckout(order, false);
    }

    private PlanEntity premiumPlan() {
        return planRepository.findByCodeAndActiveTrue(PlanCode.PREMIUM)
                .orElseThrow(() -> new IllegalStateException("The active PREMIUM plan seed is missing"));
    }

    private int configuredPrice(PlanEntity premium) {
        Integer price = premium.getPriceCents();
        if (price == null || price <= 0) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "PREMIUM_PRICE_NOT_CONFIGURED",
                    "Premium price not configured",
                    "Premium checkout is not available until an official price is configured"
            );
        }
        return price;
    }

    private void requireProviderConfigured() {
        if (!paymentProvider.isConfigured()) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "Payment provider unavailable",
                    "PIX checkout is not available until the payment provider is configured"
            );
        }
    }

    private String requiredProviderValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Payment provider response is missing " + label);
        }
        return value;
    }

    private record PreparedCheckout(PaymentOrderEntity order, boolean replay) {}
}
