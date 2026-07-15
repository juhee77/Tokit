package com.tokit.domain.relayer.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.relayer.entity.RelayerNonce;
import com.tokit.domain.relayer.repository.RelayerNonceRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelayerService {

    private final RelayerNonceRepository relayerNonceRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final WalletRepository walletRepository;
    private final ContractService contractService;

    @Transactional
    public Long getOrCreateNonce(String walletAddress) {
        String address = walletAddress.toLowerCase();
        return relayerNonceRepository.findById(address)
                .map(RelayerNonce::getNextNonce)
                .orElseGet(() -> {
                    RelayerNonce newNonce = RelayerNonce.builder()
                            .walletAddress(address)
                            .nextNonce(0L)
                            .build();
                    relayerNonceRepository.save(newNonce);
                    return 0L;
                });
    }

    @Transactional
    public void verifySignatureAndTransfer(
            String fromAddress,
            String toAddress,
            String assetSymbol,
            BigDecimal amount,
            Long nonce,
            String signature
    ) {
        log.info("Relaying transfer from {} to {}. Asset: {}, Amount: {}, Nonce: {}",
                fromAddress, toAddress, assetSymbol, amount, nonce);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("전송 수량은 0보다 커야 합니다.");
        }

        String lowerFrom = fromAddress.toLowerCase();
        RelayerNonce relayerNonce = relayerNonceRepository.findById(lowerFrom)
                .orElseGet(() -> relayerNonceRepository.save(
                        RelayerNonce.builder().walletAddress(lowerFrom).nextNonce(0L).build()
                ));

        if (!relayerNonce.getNextNonce().equals(nonce)) {
            throw new IllegalArgumentException("유효하지 않은 논스 값입니다. 기대값: " + relayerNonce.getNextNonce());
        }

        String plainMessage = fromAddress.toLowerCase() + ":" +
                toAddress.toLowerCase() + ":" +
                assetSymbol + ":" +
                amount.stripTrailingZeros().toPlainString() + ":" +
                nonce;

        if (!verifyEthereumSignature(plainMessage, signature, fromAddress)) {
            throw new IllegalArgumentException("원장 이체 전자서명 인증에 실패했습니다.");
        }

        relayerNonce.checkAndIncrementLimit();
        relayerNonce.incrementNonce();
        relayerNonceRepository.save(relayerNonce);

        Asset asset = assetRepository.findBySymbol(assetSymbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자산입니다."));

        User fromUser = userRepository.findByWalletAddressIgnoreCase(fromAddress)
                .orElseThrow(() -> new IllegalArgumentException("송신자 지갑을 찾을 수 없습니다."));

        User toUser = userRepository.findByWalletAddressIgnoreCase(toAddress)
                .orElseThrow(() -> new IllegalArgumentException("수신자 지갑을 찾을 수 없습니다."));

        Wallet fromWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(fromUser.getId(), asset.getId())
                .orElseThrow(() -> new IllegalArgumentException("송신자의 토큰 지갑이 존재하지 않습니다."));

        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("송신자의 토큰 잔고가 부족합니다.");
        }

        Wallet toWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(toUser.getId(), asset.getId())
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder()
                                .user(toUser)
                                .asset(asset)
                                .balance(BigDecimal.ZERO)
                                .lockedBalance(BigDecimal.ZERO)
                                .build()
                ));

        fromWallet.updateBalance(fromWallet.getBalance().subtract(amount), fromWallet.getLockedBalance());
        toWallet.updateBalance(toWallet.getBalance().add(amount), toWallet.getLockedBalance());
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        contractService.handleTransferByPartition(
                asset.getContractAddress(),
                asset.getSymbol(),
                "DEFAULT",
                fromAddress,
                toAddress,
                amount
        );

        log.info("Successfully completed compliance-approved gas-exempt transfer on-chain.");
    }

    private boolean verifyEthereumSignature(String message, String signature, String expectedAddress) {
        try {
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            String prefix = "\u0019Ethereum Signed Message:\n" + msgBytes.length;
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);

            byte[] totalMsg = new byte[prefixBytes.length + msgBytes.length];
            System.arraycopy(prefixBytes, 0, totalMsg, 0, prefixBytes.length);
            System.arraycopy(msgBytes, 0, totalMsg, prefixBytes.length, msgBytes.length);

            byte[] messageHash = Hash.sha3(totalMsg);

            byte[] signatureBytes = Numeric.hexStringToByteArray(signature);
            byte v = signatureBytes[64];
            if (v < 27) {
                v += 27; 
            }
            byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
            byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);

            Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            BigInteger publicKey = Sign.signedMessageHashToKey(messageHash, signatureData);
            String recoveredAddress = "0x" + Keys.getAddress(publicKey);

            return recoveredAddress.equalsIgnoreCase(expectedAddress);
        } catch (Exception e) {
            log.error("Failed to recover public key from ethereum signature", e);
            return false;
        }
    }
}
