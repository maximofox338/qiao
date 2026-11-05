package com.qiao.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TeamApplyRequest(@NotNull Long teamId,
                               @Size(max = 5000) String coverNote,
                               @NotEmpty @Size(max = 20) List<@NotNull @Valid PriceRequest> prices,
                               @NotEmpty @Size(max = 10) List<@NotNull @Valid ShareRequest> shares) {
}
