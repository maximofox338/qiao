package com.qiao.compliance.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Detects employment-like and sensitive-sector wording (spec §1.1, §1.2). Hits are flagged, never auto-rejected.
 * Latin keywords match on word boundaries (with optional plural), CJK keywords match as substrings.
 */
@Component
public class KeywordScanner {

    static final List<String> EMPLOYMENT_KEYWORDS = List.of(
            "full-time", "full time", "fulltime", "permanent", "salary", "monthly salary", "9-to-5", "office hours",
            "8 hours a day", "40 hours a week", "全职", "月薪", "工资", "坐班", "朝九晚五", "雇佣",
            "tiempo completo", "sueldo", "jornada laboral", "horario de oficina");

    static final List<String> SENSITIVE_KEYWORDS = List.of(
            "crypto", "blockchain", "token sale", "semiconductor", "chip design", "lithography", "drone", "uav",
            "lidar", "military", "defense", "defence", "government data", "state-owned", "survey", "mapping",
            "geodata", "gis", "rare earth", "lithium", "battery cathode", "genome", "biometric",
            "research assistant", "expert network", "data collection", "军", "国防", "无人机", "测绘", "地理信息",
            "芯片", "半导体", "稀土", "加密货币", "区块链", "调研", "数据采集");

    private final List<Rule> rules;

    public KeywordScanner() {
        List<Rule> all = new ArrayList<>();
        EMPLOYMENT_KEYWORDS.forEach(k -> all.add(new Rule(KeywordFlag.Type.EMPLOYMENT, k, compile(k))));
        SENSITIVE_KEYWORDS.forEach(k -> all.add(new Rule(KeywordFlag.Type.SENSITIVE, k, compile(k))));
        this.rules = List.copyOf(all);
    }

    public List<KeywordFlag> scan(Collection<String> texts) {
        String text = texts.stream().filter(Objects::nonNull).collect(Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);
        Set<KeywordFlag> hits = new LinkedHashSet<>();
        for (Rule rule : rules) {
            if (rule.pattern().matcher(text).find()) {
                hits.add(new KeywordFlag(rule.type(), rule.keyword()));
            }
        }
        return List.copyOf(hits);
    }

    public boolean matchesAny(String text) {
        return !scan(List.of(text)).isEmpty();
    }

    private static Pattern compile(String keyword) {
        if (keyword.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
            return Pattern.compile(Pattern.quote(keyword));
        }
        String body = Stream.of(keyword.split(" ")).map(Pattern::quote).collect(Collectors.joining("\\s+"));
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + body + "(?:s|es)?(?![\\p{L}\\p{N}])");
    }

    private record Rule(KeywordFlag.Type type, String keyword, Pattern pattern) {
    }
}
