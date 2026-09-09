package com.example.wallet.transfer.demo.controller;

import com.example.wallet.transfer.demo.model.Transfer;
import com.example.wallet.transfer.demo.model.TransferRequest;
import com.example.wallet.transfer.demo.service.TransferService;
import com.example.wallet.transfer.demo.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Transfers")
public class TransferController {

    private final TransferService transferService;
    private final WalletService walletService;

    public TransferController(TransferService transferService, WalletService walletService) {
        this.transferService = transferService;
        this.walletService = walletService;
    }

    @PostMapping("/transfers")
    @Operation(summary = "Create a P2P transfer between two wallets")
    public ResponseEntity<?> createTransfer(@RequestBody TransferRequest request) {
        if (request.getFrom() == null || request.getTo() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "'from' and 'to' wallet IDs are required"));
        }
        if (request.getFrom().equals(request.getTo())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot transfer to the same wallet"));
        }
        if (request.getAmountPaise() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "amount_paise must be a positive integer"));
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "idempotency_key is required"));
        }
        if (walletService.findById(request.getFrom()).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Source wallet not found"));
        }
        if (walletService.findById(request.getTo()).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Destination wallet not found"));
        }

        Transfer transfer = transferService.executeTransfer(
                request.getIdempotencyKey(),
                request.getFrom(),
                request.getTo(),
                request.getAmountPaise());

        return ResponseEntity.ok(transfer);
    }

    @GetMapping("/transfers/{id}")
    @Operation(summary = "Get transfer by ID")
    public ResponseEntity<?> getTransfer(@PathVariable Long id) {
        return transferService.findById(id)
                .map(t -> ResponseEntity.ok((Object) t))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Transfer not found")));
    }
}
