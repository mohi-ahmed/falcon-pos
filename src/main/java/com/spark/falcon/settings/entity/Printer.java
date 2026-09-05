package com.spark.falcon.settings.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.entity.enumtype.PrinterConnectionType;
import com.spark.falcon.settings.entity.enumtype.PrinterType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "printers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Printer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "business_id", nullable = false, updatable = false) private Long businessId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_printer_business"))
    @Getter(AccessLevel.NONE) private Business businessReference;
    @Column(nullable = false, length = 120) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "printer_type", nullable = false, length = 40) private PrinterType printerType;
    @Enumerated(EnumType.STRING) @Column(name = "connection_type", nullable = false, length = 40) private PrinterConnectionType connectionType;
    @Column(name = "characters_per_line", nullable = false) private int charactersPerLine;
    @Column(name = "printer_path", length = 240) private String printerPath;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column private Integer port;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ConfigurationStatus status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    public static Printer create(Long businessId, String title, PrinterType printerType,
                                 PrinterConnectionType connectionType, int charactersPerLine,
                                 String printerPath, String ipAddress, Integer port, int displayOrder, Instant now) {
        Printer printer = new Printer();
        printer.businessId = Objects.requireNonNull(businessId);
        printer.updateValues(title, printerType, connectionType, charactersPerLine, printerPath, ipAddress, port, displayOrder);
        printer.status = ConfigurationStatus.ACTIVE;
        printer.createdAt = Objects.requireNonNull(now);
        printer.updatedAt = now;
        return printer;
    }

    public void update(String title, PrinterType printerType, PrinterConnectionType connectionType,
                       int charactersPerLine, String printerPath, String ipAddress, Integer port,
                       int displayOrder, Instant now) {
        ensureNotArchived();
        updateValues(title, printerType, connectionType, charactersPerLine, printerPath, ipAddress, port, displayOrder);
        updatedAt = Objects.requireNonNull(now);
    }

    public void changeStatus(ConfigurationStatus status, Instant now) {
        ensureNotArchived();
        this.status = Objects.requireNonNull(status);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void archive(Instant now) {
        if (archivedAt != null) return;
        status = ConfigurationStatus.INACTIVE;
        archivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public boolean isArchived() { return archivedAt != null; }

    private void updateValues(String title, PrinterType printerType, PrinterConnectionType connectionType,
                              int charactersPerLine, String printerPath, String ipAddress, Integer port,
                              int displayOrder) {
        this.title = required(title, "title");
        this.printerType = Objects.requireNonNull(printerType);
        this.connectionType = Objects.requireNonNull(connectionType);
        this.charactersPerLine = charactersPerLine;
        this.printerPath = optional(printerPath);
        this.ipAddress = optional(ipAddress);
        this.port = port;
        this.displayOrder = displayOrder;
    }

    private void ensureNotArchived() { if (archivedAt != null) throw new IllegalStateException("Archived printer cannot be changed"); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
