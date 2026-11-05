package com.qiao.client.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(min = 2, max = 2) String country,
        @Size(max = 255) @Pattern(regexp = "https?://\\S+") String website,
        @Size(max = 20) String size) {
}
