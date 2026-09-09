package com.example.wallet.transfer.demo.service;

import com.example.wallet.transfer.demo.exception.IdempotencyConflictException;
import com.example.wallet.transfer.demo.metrics.DomainMetrics;
import com.example.wallet.transfer.demo.model.Transfer;
import com.example.wallet.transfer.demo.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    @PersistenceContext
    private EntityManager em;

    private final TransferRepository transferRepository;
    private final DomainMetrics metrics;

    public TransferService(TransferRepository transferRepository, DomainMetrics metrics) {
        this.transferRepository = transferRepository;
        this.metrics = metrics;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Transfer executeTransfer(String idempotencyKey, long fromWalletId, long toWalletId, long amountPaise) {
        String bodyHash = computeBodyHash(fromWalletId, toWalletId, amountPaise);

        List<Object> inserted = em.createNativeQuery(
                "INSERT INTO transfers (idempotency_key, body_hash, from_wallet_id, to_wallet_id, amount_paise, status, created_at) " +
                "VALUES (:key, :hash, :from, :to, :amount, 'PENDING', now()) " +
                "ON CONFLICT (idempotency_key) DO NOTHING " +
                "RETURNING id")
                .setParameter("key", idempotencyKey)
                .setParameter("hash", bodyHash)
                .setParameter("from", fromWalletId)
                .setParameter("to", toWalletId)
                .setParameter("amount", amountPaise)
                .getResultList();

        if (inserted.isEmpty()) {
            Transfer existing = transferRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Transfer vanished after ON CONFLICT"));

            if (!bodyHash.equals(existing.getBodyHash())) {
                log.warn("transfer.idempotency_conflict key={}", idempotencyKey);
                throw new IdempotencyConflictException(idempotencyKey);
            }

            log.info("transfer.idempotent_replay key={} transfer_id={}", idempotencyKey, existing.getId());
            metrics.incrementTransfersReplayed();
            return existing;
        }

        Long transferId = ((Number) inserted.get(0)).longValue();

        int debited = em.createNativeQuery(
                "UPDATE wallets SET balance = balance - :amount WHERE id = :id AND balance >= :amount")
                .setParameter("amount", amountPaise)
                .setParameter("id", fromWalletId)
                .executeUpdate();

        if (debited == 0) {
            em.createNativeQuery("UPDATE transfers SET status = 'DECLINED', decline_reason = 'INSUFFICIENT_FUNDS' WHERE id = :id")
                    .setParameter("id", transferId)
                    .executeUpdate();

            log.info("transfer.declined id={} reason=INSUFFICIENT_FUNDS from={} to={} amount={}",
                    transferId, fromWalletId, toWalletId, amountPaise);
            metrics.incrementTransfersDeclined();

            em.flush();
            em.clear();
            return transferRepository.findById(transferId).orElseThrow();
        }

        em.createNativeQuery("UPDATE wallets SET balance = balance + :amount WHERE id = :id")
                .setParameter("amount", amountPaise)
                .setParameter("id", toWalletId)
                .executeUpdate();

        em.createNativeQuery("UPDATE transfers SET status = 'COMPLETED' WHERE id = :id")
                .setParameter("id", transferId)
                .executeUpdate();

        log.info("transfer.completed id={} from={} to={} amount={}", transferId, fromWalletId, toWalletId, amountPaise);
        metrics.incrementTransfersCompleted();

        em.flush();
        em.clear();
        return transferRepository.findById(transferId).orElseThrow();
    }

    public Optional<Transfer> findById(Long id) {
        return transferRepository.findById(id);
    }

    private String computeBodyHash(long fromWalletId, long toWalletId, long amountPaise) {
        String raw = fromWalletId + ":" + toWalletId + ":" + amountPaise;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
