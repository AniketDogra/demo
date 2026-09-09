package com.example.wallet.transfer.demo.service;

import com.example.wallet.transfer.demo.metrics.DomainMetrics;
import com.example.wallet.transfer.demo.model.Wallet;
import com.example.wallet.transfer.demo.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    @PersistenceContext
    private EntityManager em;

    private final WalletRepository walletRepository;
    private final DomainMetrics metrics;

    public WalletService(WalletRepository walletRepository, DomainMetrics metrics) {
        this.walletRepository = walletRepository;
        this.metrics = metrics;
    }

    @Transactional
    public Wallet getOrCreate(String userId, long initialBalance) {
        int inserted = em.createNativeQuery(
                "INSERT INTO wallets (user_id, balance, created_at) VALUES (:userId, :balance, now()) " +
                "ON CONFLICT (user_id) DO NOTHING")
                .setParameter("userId", userId)
                .setParameter("balance", initialBalance)
                .executeUpdate();

        if (inserted > 0) {
            log.info("wallet.created user_id={}", userId);
            metrics.incrementWalletsCreated();
        } else {
            log.info("wallet.existing user_id={}", userId);
            metrics.incrementWalletsConflict();
        }

        em.flush();
        em.clear();

        return walletRepository.findByUserId(userId).orElseThrow();
    }

    public Optional<Wallet> findById(Long id) {
        return walletRepository.findById(id);
    }
}
