package com.qiao.compliance;

import com.qiao.compliance.service.KeywordFlag;
import com.qiao.compliance.service.KeywordScanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordScannerTest {

    private final KeywordScanner scanner = new KeywordScanner();

    @Test
    void matchesLatinKeywordsOnWordBoundariesIncludingPlurals() {
        assertThat(scanner.scan(List.of("We need a FULL-TIME engineer for drones and LiDAR")))
                .contains(new KeywordFlag(KeywordFlag.Type.EMPLOYMENT, "full-time"),
                        new KeywordFlag(KeywordFlag.Type.SENSITIVE, "drone"),
                        new KeywordFlag(KeywordFlag.Type.SENSITIVE, "lidar"));
        assertThat(scanner.scan(List.of("Logistics dashboard with mapping-free charts"))).extracting(KeywordFlag::keyword)
                .doesNotContain("gis").contains("mapping");
        assertThat(scanner.scan(List.of("Build a REST API for invoices"))).isEmpty();
    }

    @Test
    void matchesMultiWordAndChineseKeywords() {
        assertThat(scanner.scan(List.of("Work 40   hours a week", "需要全职，月薪面议", "无人机航拍")))
                .extracting(KeywordFlag::keyword)
                .contains("40 hours a week", "全职", "月薪", "无人机");
        assertThat(scanner.matchesAny("Sueldo fijo y horario de oficina")).isTrue();
    }
}
