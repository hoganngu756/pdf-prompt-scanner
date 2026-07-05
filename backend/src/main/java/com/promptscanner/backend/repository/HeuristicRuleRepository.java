package com.promptscanner.backend.repository;

import com.promptscanner.backend.entity.HeuristicRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HeuristicRuleRepository extends JpaRepository<HeuristicRule, Long> {
    List<HeuristicRule> findByIsActiveTrue();
}
