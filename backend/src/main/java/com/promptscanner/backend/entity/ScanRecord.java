package com.promptscanner.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_records")
public class ScanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private LocalDateTime scanDate;
    private boolean isSafe;
    
    @Column(length = 2000)
    private String heuristicFlags; // comma-separated or JSON
    
    @Column(length = 4000)
    private String llmExplanation;

    public ScanRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public LocalDateTime getScanDate() { return scanDate; }
    public void setScanDate(LocalDateTime scanDate) { this.scanDate = scanDate; }
    public boolean isSafe() { return isSafe; }
    public void setSafe(boolean safe) { isSafe = safe; }
    public String getHeuristicFlags() { return heuristicFlags; }
    public void setHeuristicFlags(String heuristicFlags) { this.heuristicFlags = heuristicFlags; }
    public String getLlmExplanation() { return llmExplanation; }
    public void setLlmExplanation(String llmExplanation) { this.llmExplanation = llmExplanation; }
}
