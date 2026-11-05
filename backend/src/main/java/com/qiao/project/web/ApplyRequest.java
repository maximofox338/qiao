package com.qiao.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplyRequest(@Size(max = 5000) String coverNote, @NotEmpty @Size(max = 20) List<@Valid PriceRequest> prices) {
}
