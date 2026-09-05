package com.meridianbank.account.repository;

import com.meridianbank.account.domain.AccountStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, UUID> {

    List<AccountStatusHistory> findByAccountIdOrderByChangedAtDesc(UUID accountId);
}
