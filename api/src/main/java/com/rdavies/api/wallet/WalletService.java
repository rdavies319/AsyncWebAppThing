package com.rdavies.api.wallet;

import org.springframework.stereotype.Service;

@Service
public class WalletService {
    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public Wallet getWallet(Long userId) {
        return walletRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user"));
    }
}
