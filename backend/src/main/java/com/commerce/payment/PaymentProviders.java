package com.commerce.payment;

import com.commerce.config.AppProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves the configured online provider. Returns empty when none is configured (online payment disabled). */
@Component
public class PaymentProviders {
    private final List<PaymentProvider> providers;
    private final AppProperties props;

    public PaymentProviders(List<PaymentProvider> providers, AppProperties props) {
        this.providers = providers;
        this.props = props;
    }

    public Optional<PaymentProvider> online() {
        String mode = props.getPayment().getMode();
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(mode)).findFirst();
    }

    public Optional<PaymentProvider> byCode(String code) {
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(code)).findFirst();
    }
}
