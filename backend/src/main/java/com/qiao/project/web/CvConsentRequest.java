package com.qiao.project.web;

import jakarta.validation.constraints.NotNull;

public record CvConsentRequest(@NotNull Boolean granted) {
}
