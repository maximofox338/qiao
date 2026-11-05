package com.qiao.project.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.project.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PublicCategoryController {

    private final CategoryService categoryService;

    public PublicCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/api/public/categories")
    public ApiResponse<List<CategoryView>> list() {
        return ApiResponse.ok(categoryService.list());
    }
}
