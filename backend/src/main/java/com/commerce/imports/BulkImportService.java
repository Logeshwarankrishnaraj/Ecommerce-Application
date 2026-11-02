package com.commerce.imports;

import com.commerce.audit.AuditService;
import com.commerce.catalog.CatalogDtos.AdminProduct;
import com.commerce.catalog.CatalogDtos.AttributeInput;
import com.commerce.catalog.CatalogDtos.ProductUpsert;
import com.commerce.catalog.CatalogRepositories.CategoryRepository;
import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.catalog.AdminProductService;
import com.commerce.catalog.Category;
import com.commerce.catalog.Product;
import com.commerce.common.exception.BusinessRuleException;
import com.commerce.common.exception.ConflictException;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.common.util.Hashing;
import com.commerce.config.AppProperties;
import com.commerce.imports.ImportRepositories.*;
import com.commerce.lookup.AttributeDefinition;
import com.commerce.lookup.LookupRepositories.AttributeDefinitionRepository;
import com.commerce.lookup.LookupRepositories.ListOfValueRepository;
import com.commerce.security.CurrentUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CSV (+ optional ZIP of images) product import with a mandatory preview step.
 * Default is all-or-nothing: nothing is written unless every row is valid (SRS bulk upload rules).
 */
@Service
public class BulkImportService {
    public static final List<String> HEADER = List.of("sku", "title", "description", "category_slug", "base_price", "sale_price", "currency",
            "initial_stock", "low_stock_threshold", "seo_title", "seo_description", "material", "jewelry_type", "color", "stone_type", "size",
            "image_files", "image_alt");
    static final int MAX_ROWS = 1000;
    static final long MAX_CSV_BYTES = 2L * 1024 * 1024;
    static final long MAX_ZIP_BYTES = 40L * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 600;

    public record RowError(String column, String message) {}
    public record RowView(int rowNumber, String sku, boolean valid, String result, List<RowError> errors) {}
    public record JobView(Long jobId, String status, String mode, boolean allowPartial, String filename, int totalRows, int validRows, int invalidRows,
                          int committedRows, java.time.Instant createdAt, List<RowView> rows) {}

    private record Parsed(int number, Map<String, String> cells) {}

    private final JobRepository jobs;
    private final RowRepository rows;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final AttributeDefinitionRepository definitions;
    private final ListOfValueRepository lovs;
    private final AdminProductService adminProducts;
    private final AuditService audit;
    private final AppProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BulkImportService(JobRepository jobs, RowRepository rows, CategoryRepository categories, ProductRepository products,
                             AttributeDefinitionRepository definitions, ListOfValueRepository lovs, AdminProductService adminProducts, AuditService audit,
                             AppProperties props, PlatformTransactionManager txm, Clock clock) {
        this.jobs = jobs;
        this.rows = rows;
        this.categories = categories;
        this.products = products;
        this.definitions = definitions;
        this.lovs = lovs;
        this.adminProducts = adminProducts;
        this.audit = audit;
        this.props = props;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    public String template() {
        return Csv.line(HEADER) + Csv.line(List.of("RING-001", "Classic gold ring", "Hand finished 18k gold ring", "rings", "24999.00", "", "INR", "10", "3",
                "", "", "GOLD", "RING", "", "", "", "ring-001-front.jpg;ring-001-side.jpg", "Classic gold ring"));
    }

    // ------------------------------------------------------------------ preview

    public JobView preview(String filename, byte[] csvBytes, byte[] zipBytes, String mode, boolean allowPartial) {
        String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!m.equals("CREATE") && !m.equals("UPDATE")) throw new ValidationFailedException("mode", "Mode must be CREATE or UPDATE.");
        if (csvBytes == null || csvBytes.length == 0) throw new ValidationFailedException("file", "Choose a CSV file.");
        if (csvBytes.length > MAX_CSV_BYTES) throw new ValidationFailedException("file", "The CSV file is larger than 2 MB.");
        Map<String, byte[]> images = zipBytes == null || zipBytes.length == 0 ? Map.of() : unzip(zipBytes);
        List<List<String>> table = Csv.parse(new String(csvBytes, StandardCharsets.UTF_8));
        if (table.isEmpty()) throw new ValidationFailedException("file", "The CSV file is empty.");
        List<String> head = table.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        for (String required : List.of("sku", "title", "category_slug", "base_price")) {
            if (!head.contains(required)) throw new ValidationFailedException("file", "Missing required column '" + required + "'. Download the template.");
        }
        for (String h : head) if (!HEADER.contains(h)) throw new ValidationFailedException("file", "Unknown column '" + h.replaceAll("[^a-z0-9_]", "?") + "'.");
        if (table.size() - 1 > MAX_ROWS) throw new ValidationFailedException("file", "At most " + MAX_ROWS + " rows per import.");
        if (table.size() == 1) throw new ValidationFailedException("file", "The CSV file has no data rows.");

        List<Parsed> parsed = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < head.size(); c++) cells.put(head.get(c), c < table.get(i).size() ? table.get(i).get(c).trim() : "");
            parsed.add(new Parsed(i + 1, cells));   // +1: spreadsheet row number (header is row 1)
        }
        Map<String, Integer> firstSeen = new HashMap<>();
        BulkImportJob job = new BulkImportJob();
        job.setMode(m);
        job.setAllowPartial(allowPartial);
        job.setFilename(safeName(filename));
        job.setChecksum(Hashing.sha256Hex(csvBytes));
        job.setStatus("PREVIEWED");
        job.setHasImages(!images.isEmpty());
        job.setCreatedBy(CurrentUser.label());
        job.setCreatedAt(clock.instant());
        job.setUpdatedAt(clock.instant());
        List<BulkImportRow> out = new ArrayList<>();
        int valid = 0;
        for (Parsed p : parsed) {
            List<RowError> errs = validateRow(p, m, images, firstSeen);
            BulkImportRow r = new BulkImportRow();
            r.setRowNum(p.number());
            r.setSku(clip(p.cells().getOrDefault("sku", "").toUpperCase(Locale.ROOT), 64));
            r.setPayloadJson(flat(p.cells()));
            r.setValid(errs.isEmpty());
            r.setResult(errs.isEmpty() ? "READY" : "INVALID");
            r.setErrorsJson(errs.isEmpty() ? null : errs.stream().map(e -> e.column() + "\t" + e.message()).reduce((a, b) -> a + "\n" + b).orElse(null));
            if (errs.isEmpty()) valid++;
            out.add(r);
        }
        job.setTotalRows(out.size());
        job.setValidRows(valid);
        job.setInvalidRows(out.size() - valid);
        BulkImportJob saved = tx.execute(s -> {
            BulkImportJob j = jobs.save(job);
            for (BulkImportRow r : out) r.setJobId(j.getId());
            rows.saveAll(out);
            audit.record("IMPORT_PREVIEW", "IMPORT", j.getId(), j.getFilename() + ": " + job.getValidRows() + " valid, " + job.getInvalidRows() + " invalid");
            return j;
        });
        persist(saved.getId(), csvBytes, zipBytes);
        return view(saved);
    }

    private List<RowError> validateRow(Parsed p, String mode, Map<String, byte[]> images, Map<String, Integer> firstSeen) {
        List<RowError> errs = new ArrayList<>();
        Map<String, String> c = p.cells();
        String sku = c.getOrDefault("sku", "").toUpperCase(Locale.ROOT);
        if (!sku.isEmpty()) {
            Integer prev = firstSeen.putIfAbsent(sku, p.number());
            if (prev != null) errs.add(new RowError("sku", "Duplicate SKU in this file (first seen on row " + prev + ")."));
        }
        ProductUpsert upsert;
        try {
            upsert = toUpsert(c);
        } catch (ValidationFailedException e) {
            errs.add(new RowError(fieldOf(e), e.getMessage()));
            return errs;
        }
        Optional<Product> existing = sku.isEmpty() ? Optional.empty() : products.findBySku(sku);
        if ("UPDATE".equals(mode) && existing.isEmpty() && !sku.isEmpty()) errs.add(new RowError("sku", "No product with this SKU exists to update."));
        try {
            if ("CREATE".equals(mode)) adminProducts.validate(upsert, null, null);
            else adminProducts.validate(upsert, existing.map(Product::getId).orElse(null), existing.map(Product::getSlug).orElse(null));
        } catch (com.commerce.common.exception.AppException e) {
            if (!(e.getErrorCode().equals("DUPLICATE_RESOURCE") && "UPDATE".equals(mode))) errs.add(new RowError(fieldOf(e), e.getMessage()));
        }
        errs.addAll(checkAttributes(upsert.attributes()));
        errs.addAll(checkNumbers(c));
        for (String f : imageNames(c)) {
            if (!images.containsKey(f.toLowerCase(Locale.ROOT))) errs.add(new RowError("image_files", "Image '" + f.replaceAll("[^A-Za-z0-9._ -]", "?") + "' was not found in the ZIP."));
        }
        if (imageNames(c).size() > 5) errs.add(new RowError("image_files", "At most 5 images per product."));
        if (!imageNames(c).isEmpty() && c.getOrDefault("image_alt", "").length() < 3) errs.add(new RowError("image_alt", "Alt text (3+ characters) is required when images are supplied."));
        return errs;
    }

    private List<RowError> checkNumbers(Map<String, String> c) {
        List<RowError> errs = new ArrayList<>();
        for (String col : List.of("initial_stock", "low_stock_threshold")) {
            String v = c.getOrDefault(col, "");
            if (!v.isEmpty() && !v.matches("^\\d{1,7}$")) errs.add(new RowError(col, "Enter a whole number."));
        }
        return errs;
    }

    private List<RowError> checkAttributes(List<AttributeInput> attrs) {
        List<RowError> errs = new ArrayList<>();
        Map<String, AttributeDefinition> defs = new HashMap<>();
        for (AttributeDefinition d : definitions.findByActiveTrueOrderBySortOrderAsc()) defs.put(d.getCode(), d);
        for (AttributeInput a : attrs) {
            AttributeDefinition d = defs.get(a.code());
            if (d == null) { errs.add(new RowError(a.code(), "Unsupported attribute.")); continue; }
            if (d.getLovGroupCode() != null) {
                var lov = lovs.findByGroupCodeAndValueCode(d.getLovGroupCode(), a.valueCode());
                if (lov.isEmpty() || !lov.get().isActive()) errs.add(new RowError(a.code(), "'" + a.valueCode().replaceAll("[^A-Za-z0-9_]", "?") + "' is not an available " + d.getLabel() + " value."));
            }
        }
        return errs;
    }

    private ProductUpsert toUpsert(Map<String, String> c) {
        String slug = c.getOrDefault("category_slug", "");
        Category cat = categories.findBySlug(slug).orElse(null);
        if (cat == null) throw new ValidationFailedException("category_slug", "Category '" + slug.replaceAll("[^A-Za-z0-9-]", "?") + "' was not found.");
        List<AttributeInput> attrs = new ArrayList<>();
        for (String code : List.of("material", "jewelry_type", "color", "stone_type", "size")) {
            String v = c.getOrDefault(code, "");
            if (!v.isEmpty()) attrs.add(new AttributeInput(code, v.toUpperCase(Locale.ROOT), null));
        }
        return new ProductUpsert(c.getOrDefault("sku", ""), c.getOrDefault("title", ""), null, c.getOrDefault("description", ""), cat.getId(),
                money("base_price", c), moneyOrNull("sale_price", c), blankNull(c.get("currency")), blankNull(c.get("seo_title")), blankNull(c.get("seo_description")),
                intOrNull(c.get("low_stock_threshold")), intOrNull(c.get("initial_stock")), null, null, attrs);
    }

    private static BigDecimal money(String col, Map<String, String> c) {
        BigDecimal v = moneyOrNull(col, c);
        if (v == null) throw new ValidationFailedException(col, "Price is required.");
        return v;
    }

    private static BigDecimal moneyOrNull(String col, Map<String, String> c) {
        String v = c.getOrDefault(col, "");
        if (v.isEmpty()) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new ValidationFailedException(col, "Enter a number like 1999.00.");
        }
    }

    private static Integer intOrNull(String v) {
        try {
            return v == null || v.isBlank() ? null : Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;   // reported separately by checkNumbers
        }
    }

    private static String fieldOf(com.commerce.common.exception.AppException e) {
        return e.getFieldErrors().isEmpty() ? "row" : e.getFieldErrors().get(0).field();
    }

    // ------------------------------------------------------------------ commit

    public JobView commit(Long jobId) {
        BulkImportJob job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Import"));
        if (!"PREVIEWED".equals(job.getStatus())) throw new ConflictException("This import has already been processed.");
        if (!job.isAllowPartial() && job.getInvalidRows() > 0) {
            throw new BusinessRuleException("Fix the " + job.getInvalidRows() + " invalid row(s) first. Nothing was imported.");
        }
        if (job.getValidRows() == 0) throw new BusinessRuleException("There are no valid rows to import.");
        List<BulkImportRow> all = rows.findByJobIdOrderByRowNumAsc(jobId);
        Map<Integer, Parsed> byRow = reparse(jobId);
        Map<String, byte[]> images = job.isHasImages() ? unzip(readFile(jobId, "zip")) : Map.of();
        int done = 0;
        if (!job.isAllowPartial()) {
            tx.executeWithoutResult(s -> apply(all, byRow, images, job.getMode(), false));   // one transaction: any failure rolls everything back
            done = (int) all.stream().filter(BulkImportRow::isValid).count();
        } else {
            for (BulkImportRow r : all) {
                if (!r.isValid()) continue;
                try {
                    tx.executeWithoutResult(s -> apply(List.of(r), byRow, images, job.getMode(), true));
                    done++;
                } catch (RuntimeException e) {
                    r.setValid(false);
                    r.setResult("FAILED");
                    r.setErrorsJson("row\t" + (e instanceof com.commerce.common.exception.AppException ? e.getMessage() : "Unexpected error while saving this row."));
                    rows.save(r);
                }
            }
        }
        final int committed = done;
        tx.executeWithoutResult(s -> {
            BulkImportJob j = jobs.findById(jobId).orElseThrow();
            j.setStatus("COMMITTED");
            j.setCommittedRows(committed);
            j.setUpdatedAt(clock.instant());
            audit.record("IMPORT_COMMIT", "IMPORT", jobId, committed + " row(s) imported (" + job.getMode() + ")");
        });
        return view(jobs.findById(jobId).orElseThrow());
    }

    private void apply(List<BulkImportRow> target, Map<Integer, Parsed> byRow, Map<String, byte[]> images, String mode, boolean partial) {
        for (BulkImportRow r : target) {
            if (!r.isValid()) continue;
            Map<String, String> c = byRow.get(r.getRowNum()).cells();
            ProductUpsert u = toUpsert(c);
            Long id;
            if ("CREATE".equals(mode)) {
                id = adminProducts.create(u).productId();
            } else {
                Product p = products.findBySku(r.getSku()).orElseThrow(() -> new ResourceNotFoundException("Product " + r.getSku()));
                id = adminProducts.update(p.getId(), u).productId();
            }
            String alt = c.getOrDefault("image_alt", "");
            for (String f : imageNames(c)) adminProducts.addImage(id, images.get(f.toLowerCase(Locale.ROOT)), alt);
            r.setProductId(id);
            r.setResult("CREATE".equals(mode) ? "CREATED" : "UPDATED");
            rows.save(r);
        }
    }

    private Map<Integer, Parsed> reparse(Long jobId) {
        List<List<String>> table = Csv.parse(new String(readFile(jobId, "csv"), StandardCharsets.UTF_8));
        List<String> head = table.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        Map<Integer, Parsed> out = new HashMap<>();
        for (int i = 1; i < table.size(); i++) {
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < head.size(); c++) cells.put(head.get(c), c < table.get(i).size() ? table.get(i).get(c).trim() : "");
            out.put(i + 1, new Parsed(i + 1, cells));
        }
        return out;
    }

    // ------------------------------------------------------------------ queries

    public JobView get(Long jobId) {
        return view(jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Import")));
    }

    public String errorsCsv(Long jobId) {
        jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Import"));
        StringBuilder sb = new StringBuilder(Csv.line(List.of("row", "sku", "column", "error")));
        for (BulkImportRow r : rows.findByJobIdOrderByRowNumAsc(jobId)) {
            for (RowError e : errors(r)) sb.append(Csv.line(List.of(String.valueOf(r.getRowNum()), r.getSku() == null ? "" : r.getSku(), e.column(), e.message())));
        }
        return sb.toString();
    }

    private JobView view(BulkImportJob j) {
        List<RowView> rv = rows.findByJobIdOrderByRowNumAsc(j.getId()).stream()
                .map(r -> new RowView(r.getRowNum(), r.getSku(), r.isValid(), r.getResult(), errors(r))).toList();
        return new JobView(j.getId(), j.getStatus(), j.getMode(), j.isAllowPartial(), j.getFilename(), j.getTotalRows(), j.getValidRows(), j.getInvalidRows(),
                j.getCommittedRows(), j.getCreatedAt(), rv);
    }

    private static List<RowError> errors(BulkImportRow r) {
        if (r.getErrorsJson() == null || r.getErrorsJson().isBlank()) return List.of();
        List<RowError> out = new ArrayList<>();
        for (String line : r.getErrorsJson().split("\n")) {
            int t = line.indexOf('\t');
            out.add(t < 0 ? new RowError("row", line) : new RowError(line.substring(0, t), line.substring(t + 1)));
        }
        return out;
    }

    // ------------------------------------------------------------------ files

    static List<String> imageNames(Map<String, String> c) {
        String v = c.getOrDefault("image_files", "");
        if (v.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : v.split("[;|]")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    /** Reads a ZIP defensively: entry-count and size caps (zip bombs), basenames only (no path traversal), JPEG/PNG only. */
    static Map<String, byte[]> unzip(byte[] zip) {
        if (zip.length > MAX_ZIP_BYTES) throw new ValidationFailedException("images", "The ZIP file is larger than 40 MB.");
        Map<String, byte[]> out = new HashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (++entries > MAX_ZIP_ENTRIES) throw new ValidationFailedException("images", "The ZIP has too many files.");
                String name = e.getName().replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (name.startsWith(".") || name.startsWith("__")) continue;
                if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                long entrySize = 0;
                while ((n = in.read(buf)) > 0) {
                    entrySize += n;
                    total += n;
                    if (entrySize > 6_000_000L || total > 120_000_000L) throw new ValidationFailedException("images", "The ZIP contains files that are too large.");
                    bos.write(buf, 0, n);
                }
                out.put(name, bos.toByteArray());
            }
        } catch (IOException ex) {
            throw new ValidationFailedException("images", "The ZIP file could not be read.");
        }
        return out;
    }

    private Path dir() {
        Path d = Path.of(props.getMedia().getStoragePath()).toAbsolutePath().normalize().resolve("imports");   // private: not served by /media
        try {
            Files.createDirectories(d);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create import directory", e);
        }
        return d;
    }

    private void persist(Long jobId, byte[] csv, byte[] zip) {
        try {
            Files.write(dir().resolve(jobId + ".csv"), csv);
            if (zip != null && zip.length > 0) Files.write(dir().resolve(jobId + ".zip"), zip);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store import files", e);
        }
    }

    private byte[] readFile(Long jobId, String ext) {
        try {
            return Files.readAllBytes(dir().resolve(jobId + "." + ext));
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded files for this import are no longer available. Upload them again.");
        }
    }

    private static String flat(Map<String, String> cells) {
        StringBuilder sb = new StringBuilder();
        cells.forEach((k, v) -> { if (!v.isEmpty()) sb.append(k).append('=').append(clip(v.replace('\n', ' '), 200)).append('\n'); });
        return clip(sb.toString(), 5000);
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static String blankNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String safeName(String n) {
        String t = n == null ? "import.csv" : n.replaceAll("[^A-Za-z0-9._ -]", "_");
        return clip(t.isBlank() ? "import.csv" : t, 200);
    }
}
