package com.tokit.domain.issuer.service;

import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.user.entity.Role;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 발행사 정산 계정을 보장한다.
 *
 * <p>발행사도 원화 지갑이 필요하다. 배당을 원 단위로 절사하면 주주 1명당 최대 1원이 남고,
 * 그 잔액은 재원을 넣은 발행사에게 돌아가야 한다. 아무 데도 넣지 않으면 원장에서 사라진다.
 *
 * <p>계정은 미리 만들지 않고 <b>처음 필요한 시점에</b> 만든다. 이 기능 이전에 등록된
 * 발행사가 있고, 모든 발행사가 배당을 집행하는 것도 아니기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssuerSettlementAccountService {

    /** 사람이 로그인할 수 없는 값. 서비스 계정은 인증 대상이 아니다. */
    private static final String LOGIN_DISABLED = "{noop}LOGIN_DISABLED";

    private final IssuerRepository issuerRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    /**
     * 발행사의 정산용 원화 지갑을 보장하고 그 지갑의 소유자 ID를 돌려준다.
     *
     * <p>계정과 지갑이 이미 있으면 그대로 쓴다. 없으면 만든다. 호출자의 트랜잭션에
     * 참여하므로, 배당 집행이 롤백되면 계정 생성도 함께 되돌아간다.
     */
    @Transactional
    public Long ensureKrwWallet(Issuer issuer) {
        User account = (issuer.getUser() != null)
                ? issuer.getUser()
                : createServiceAccount(issuer);

        boolean hasKrwWallet = walletRepository.findKrwWalletByUserId(account.getId()).isPresent();
        if (!hasKrwWallet) {
            walletRepository.save(Wallet.builder()
                    .user(account)
                    .asset(null)                 // NULL = 원화(KRW) 지갑
                    .balance(BigDecimal.ZERO)
                    .lockedBalance(BigDecimal.ZERO)
                    .build());
            log.info("Created settlement KRW wallet for issuer {} (user {})",
                    issuer.getId(), account.getId());
        }
        return account.getId();
    }

    private User createServiceAccount(Issuer issuer) {
        String email = "issuer-" + issuer.getBizRegNo() + "@tokit.internal";

        // 재실행이나 동시 호출로 이미 만들어져 있을 수 있다. 중복 생성을 피한다.
        User account = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(issuer.getCompanyName() + " (발행사 계정)")
                        .email(email)
                        .password(LOGIN_DISABLED)
                        .walletAddress("0xISSUER" + String.format("%032d", issuer.getId()))
                        .kycStatus(true)
                        .role(Role.USER)
                        .build()));

        issuer.linkSettlementAccount(account);
        issuerRepository.save(issuer);
        log.info("Linked settlement account {} to issuer {}", account.getId(), issuer.getId());
        return account;
    }
}
