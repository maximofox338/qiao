package com.qiao.billing.web;

import com.qiao.billing.service.PaymentService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.money.FeeCalculator;
import com.qiao.common.util.Locales;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@RestController
public class PublicConfigController {

    private final FeeCalculator feeCalculator;
    private final PaymentService paymentService;

    public PublicConfigController(FeeCalculator feeCalculator, PaymentService paymentService) {
        this.feeCalculator = feeCalculator;
        this.paymentService = paymentService;
    }

    public record PublicConfig(BigDecimal feePct, String paymentProvider, List<String> locales) {
    }

    @GetMapping("/api/public/config")
    public ApiResponse<PublicConfig> config() {
        return ApiResponse.ok(new PublicConfig(feeCalculator.feePct(),
                paymentService.providerType().name().toLowerCase(Locale.ROOT), Locales.SUPPORTED));
    }
}
