package com.promptscanner.backend.repository;

import com.promptscanner.backend.entity.ScanRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanRecordRepository extends JpaRepository<ScanRecord, Long> {
    List<ScanRecord> findAllByOrderByScanDateDesc();
}
