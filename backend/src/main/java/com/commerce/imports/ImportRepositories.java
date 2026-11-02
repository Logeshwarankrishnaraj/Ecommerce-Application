package com.commerce.imports;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public final class ImportRepositories {
    private ImportRepositories() {}

    public interface JobRepository extends JpaRepository<BulkImportJob, Long> {}

    public interface RowRepository extends JpaRepository<BulkImportRow, Long> {
        List<BulkImportRow> findByJobIdOrderByRowNumAsc(Long jobId);
    }
}
