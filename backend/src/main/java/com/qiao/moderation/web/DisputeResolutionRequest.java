package com.qiao.moderation.web;

import com.qiao.contract.service.MilestoneService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisputeResolutionRequest(@NotNull MilestoneService.Resolution resolution, @Size(max = 1000) String note) {
}
