package com.qiao.project.service;

import com.qiao.account.service.PersonalDataHandler;
import com.qiao.client.service.CompanyService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engineers: applications are exported, withdrawn and stripped of cover notes on deletion.
 * Clients: the company's projects are exported and those not yet contracted are cancelled on deletion.
 */
@Component
public class ProjectPersonalDataHandler implements PersonalDataHandler {

    private final ApplicationService applicationService;
    private final ProjectService projectService;
    private final CompanyService companyService;

    public ProjectPersonalDataHandler(ApplicationService applicationService, ProjectService projectService,
                                      CompanyService companyService) {
        this.applicationService = applicationService;
        this.projectService = projectService;
        this.companyService = companyService;
    }

    @Override
    public String section() {
        return "projects";
    }

    @Override
    public Object export(long userId) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("applications", applicationService.listForEngineer(userId));
        export.put("clientProjects", companyService.findCompanyOf(userId)
                .map(company -> projectService.listForClient(userId)).orElse(List.of()));
        return export;
    }

    @Override
    public void anonymize(long userId) {
        applicationService.withdrawAllAndClearNotes(userId);
        companyService.findCompanyOf(userId)
                .ifPresent(company -> projectService.cancelActiveProjectsOfCompany(company.getId()));
    }
}
