package com.qiao.project.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.project.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('COMPLIANCE')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<AdminCategoryView>> list() {
        return ApiResponse.ok(categoryService.adminList());
    }

    @PutMapping("/{code}")
    public ApiResponse<AdminCategoryView> update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String code,
                                            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.ok(categoryService.update(code, request, principal.id()));
    }
}
