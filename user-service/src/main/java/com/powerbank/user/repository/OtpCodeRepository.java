package com.powerbank.user.repository;

import com.powerbank.user.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    Optional<OtpCode> findTopByPhoneAndVerifiedFalseOrderByCreatedAtDesc(String phone);
}
