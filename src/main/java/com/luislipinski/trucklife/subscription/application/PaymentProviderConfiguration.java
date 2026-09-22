package com.luislipinski.trucklife.subscription.application;

import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentProviderConfiguration {

    @Bean
    PaymentProvider paymentProvider() {
        return new PaymentProvider() {
            @Override
            public PaymentProviderCode code() {
                return PaymentProviderCode.MERCADO_PAGO;
            }

            @Override
            public boolean isConfigured() {
                return false;
            }

            @Override
            public PixCheckout createPix(PixRequest request) {
                throw new IllegalStateException("Mercado Pago credentials are not configured");
            }
        };
    }
}
