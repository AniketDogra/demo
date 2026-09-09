package com.example.wallet.transfer.demo.controller;

import com.example.wallet.transfer.demo.model.CreateWalletRequest;
import com.example.wallet.transfer.demo.model.Wallet;
import com.example.wallet.transfer.demo.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/wallets")
    @Operation(summary = "Get or create a wallet for the authenticated user")
    public ResponseEntity<?> createWallet(
            @RequestBody(required = false) CreateWalletRequest request,
            HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Authorization header required: Bearer <user_id>"));
        }

        long initialBalance = (request != null) ? request.effectiveBalance() : 0;
        Wallet wallet = walletService.getOrCreate(userId, initialBalance);
        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/wallets/{id}")
    @Operation(summary = "Get wallet by ID")
    public ResponseEntity<?> getWallet(@PathVariable Long id) {
        return walletService.findById(id)
                .map(w -> ResponseEntity.ok((Object) w))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Wallet not found")));
    }
}
