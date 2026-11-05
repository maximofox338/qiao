package com.qiao.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.error.ValidationCodes;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.project.domain.Category;
import com.qiao.project.domain.CategoryMapper;
import com.qiao.project.web.AdminCategoryView;
import com.qiao.project.web.CategoryUpdateRequest;
import com.qiao.project.web.CategoryView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final AuditService auditService;

    public CategoryService(CategoryMapper categoryMapper, AuditService auditService) {
        this.categoryMapper = categoryMapper;
        this.auditService = auditService;
    }

    public List<CategoryView> list() {
        return all().stream().map(CategoryView::from).toList();
    }

    public List<AdminCategoryView> adminList() {
        return all().stream().map(AdminCategoryView::from).toList();
    }

    /**
     * @throws ApiException {@code VALIDATION_ERROR} for unknown codes, {@code PROHIBITED_CATEGORY} for disallowed ones
     */
    public Category requireAllowed(String code) {
        Category category = find(code);
        if (category == null) {
            throw ApiException.validation("categoryCode", ValidationCodes.INVALID);
        }
        if (!Boolean.TRUE.equals(category.getAllowed())) {
            throw new ApiException(ErrorCode.PROHIBITED_CATEGORY);
        }
        return category;
    }

    public boolean isAllowed(String code) {
        Category category = find(code);
        return category != null && Boolean.TRUE.equals(category.getAllowed());
    }

    @Transactional
    public AdminCategoryView update(String code, CategoryUpdateRequest request, long adminId) {
        Category category = find(code);
        if (category == null) {
            throw ApiException.notFound("Category");
        }
        category.setAllowed(request.allowed());
        category.setNameEn(request.nameEn().trim());
        category.setNameEs(request.nameEs().trim());
        category.setNameZh(request.nameZh().trim());
        categoryMapper.updateById(category);
        auditService.log(Actor.admin(adminId), AuditAction.CATEGORY_UPDATED, "category", code,
                Map.of("allowed", request.allowed()));
        return AdminCategoryView.from(category);
    }

    private List<Category> all() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
    }

    private Category find(String code) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<Category>().eq(Category::getCode, code));
    }
}
