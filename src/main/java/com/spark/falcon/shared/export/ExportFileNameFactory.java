package com.spark.falcon.shared.export;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class ExportFileNameFactory {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Clock clock;

    public ExportFileNameFactory() { this(Clock.systemUTC()); }
    ExportFileNameFactory(Clock clock) { this.clock = clock; }

    public String create(String title, ExportFormat format) {
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) slug = "export";
        return slug + "-" + STAMP.format(LocalDateTime.now(clock)) + "." + format.extension();
    }
}
