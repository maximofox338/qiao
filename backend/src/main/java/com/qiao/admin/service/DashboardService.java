package com.qiao.admin.service;

import com.qiao.admin.web.DashboardView;
import com.qiao.billing.service.PaymentService;
import com.qiao.client.service.CompanyService;
import com.qiao.compliance.service.TransferService;
import com.qiao.compliance.web.TransferSummary;
import com.qiao.contract.service.ContractService;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.project.service.ProjectService;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final ProjectService projectService;
    private final ContractService contractService;
    private final PaymentService paymentService;
    private final TransferService transferService;

    public DashboardService(CompanyService companyService, EngineerProfileService profileService,
                            ProjectService projectService, ContractService contractService,
                            PaymentService paymentService, TransferService transferService) {
        this.companyService = companyService;
        this.profileService = profileService;
        this.projectService = projectService;
        this.contractService = contractService;
        this.paymentService = paymentService;
        this.transferService = transferService;
    }

    public DashboardView dashboard() {
        PaymentService.Totals totals = paymentService.totals();
        TransferSummary transfers = transferService.summary(null);
        return new DashboardView(
                new DashboardView.ByStatus(companyService.countByStatus()),
                new DashboardView.ByStatus(profileService.countByStatus()),
                new DashboardView.ByStatus(projectService.countByStatus()),
                new DashboardView.ByStatus(contractService.countByStatus()),
                totals.gmvUsd(), totals.feesUsd(),
                new DashboardView.Transfers(transfers.year(), transfers.uniqueEngineers().get("ANY"), transfers.level()));
    }
}
