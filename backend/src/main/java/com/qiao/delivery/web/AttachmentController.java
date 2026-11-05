package com.qiao.delivery.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.delivery.domain.Attachment;
import com.qiao.delivery.service.AttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(path = "/api/contracts/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentView> upload(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) Long milestoneId) {
        return ApiResponse.ok(AttachmentView.from(attachmentService.upload(principal.id(), id, file, milestoneId)));
    }

    /**
     * Always served as a download ({@code Content-Disposition: attachment}) with the sniffed content type.
     */
    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long id) {
        AttachmentService.Download download = attachmentService.download(principal.id(), id);
        Attachment attachment = download.attachment();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .body(new InputStreamResource(download.content()));
    }
}
