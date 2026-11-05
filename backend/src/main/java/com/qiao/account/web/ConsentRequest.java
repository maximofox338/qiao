package com.qiao.account.web;

import com.qiao.compliance.domain.ConsentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsentRequest(@NotNull ConsentType type, @NotNull Boolean granted, @Size(max = 64) String scopeRef) {
}
