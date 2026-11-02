package com.commerce.imports;

import jakarta.persistence.*;

@Entity
@Table(name = "bulk_import_rows")
public class BulkImportRow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long id;
    private Long jobId;
    @Column(name = "row_num")
    private int rowNum;
    private String sku;
    /** Original cells of the row, kept for audit (flat "key=value" lines). */
    @Column(columnDefinition = "TEXT")
    private String payloadJson;
    private boolean valid;
    private String result;
    /** One error per line, formatted "column<TAB>message". */
    @Column(columnDefinition = "TEXT")
    private String errorsJson;
    private Long productId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public int getRowNum() { return rowNum; }
    public void setRowNum(int rowNum) { this.rowNum = rowNum; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getErrorsJson() { return errorsJson; }
    public void setErrorsJson(String errorsJson) { this.errorsJson = errorsJson; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
}
