package com.qiao.delivery.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeSnifferTest {

    @Test
    void detectsTypeFromContentNotFromTheName() {
        assertThat(sniff("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "notes.txt")).isEqualTo("application/pdf");
        assertThat(sniff(new byte[]{'P', 'K', 3, 4, 0}, "spec.docx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(sniff("README: 交付说明".getBytes(StandardCharsets.UTF_8), "README")).isEqualTo("text/plain");
        assertThat(sniff(new byte[]{0, 1, 2, (byte) 0xFE}, "model.step")).isEqualTo("application/octet-stream");
    }

    @Test
    void rejectsExecutables() {
        assertThat(ContentTypeSniffer.sniff(new byte[]{'M', 'Z', 0}, 3, "setup.pdf")).isEmpty();
        assertThat(ContentTypeSniffer.sniff("#!/bin/sh".getBytes(StandardCharsets.US_ASCII), 9, "run.txt")).isEmpty();
    }

    @Test
    void sanitizesFilenames() {
        assertThat(AttachmentService.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(AttachmentService.sanitizeFilename("C:\\docs\\报告.pdf")).isEqualTo("报告.pdf");
        assertThat(AttachmentService.sanitizeFilename("..")).isEqualTo("file");
    }

    private static String sniff(byte[] head, String name) {
        return ContentTypeSniffer.sniff(head, head.length, name).orElseThrow();
    }
}
