package com.qiao.delivery.web;

import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitMilestoneRequest(@Size(max = 5000) String note, @Size(max = 20) List<Long> attachmentIds) {
}
