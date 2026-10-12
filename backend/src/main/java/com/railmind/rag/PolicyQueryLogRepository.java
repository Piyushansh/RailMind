package com.railmind.rag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyQueryLogRepository extends JpaRepository<PolicyQueryLog, Long> {
    long countByGroundedTrue();
    long countByGroundedFalse();
}
