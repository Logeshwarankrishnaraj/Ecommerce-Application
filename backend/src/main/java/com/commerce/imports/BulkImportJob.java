package com.commerce.imports;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bulk_import_jobs")
public class BulkImportJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;
    private String mode;
    private boolean allowPartial;
    private String filename;
    private String checksum;
    private String status;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int committedRows;
    private boolean hasImages;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public boolean isAllowPartial() { return allowPartial; }
    public void setAllowPartial(boolean allowPartial) { this.allowPartial = allowPartial; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }
    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }
    public int getCommittedRows() { return committedRows; }
    public void setCommittedRows(int committedRows) { this.committedRows = committedRows; }
    public boolean isHasImages() { return hasImages; }
    public void setHasImages(boolean hasImages) { this.hasImages = hasImages; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
