package com.example.wallet.transfer.demo.repository;

import com.example.wallet.transfer.demo.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
