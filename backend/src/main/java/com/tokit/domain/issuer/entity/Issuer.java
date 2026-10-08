package com.tokit.domain.issuer.entity;

import com.tokit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "issuers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issuer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName; // 신탁사명 (ex. 한국토지신탁)

    @Column(name = "biz_reg_no", nullable = false, unique = true)
    private String bizRegNo; // 사업자등록번호

    /**
     * 발행사 서비스 계정.
     *
     * <p>발행사도 원화 지갑을 가져야 한다. 배당 절사로 남은 미분배 잔액을 돌려받는 상대
     * 계정이기 때문이다. {@code wallets}는 사용자에 매달려 있으므로(user_id NOT NULL),
     * 발행사마다 서비스 계정을 두어 기존 지갑 로직(비관적 락 포함)을 그대로 재사용한다.
     * 로그인은 불가능한 계정이다.
     *
     * <p>이 값은 비어 있을 수 있다. 이 기능 이전에 만들어진 발행사가 있고, 정산 계정이
     * 실제로 필요한 시점은 배당 잔액을 처음 돌려줄 때이기 때문이다. 없으면 그때 만든다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** 정산 계정을 연결한다. 최초 배당 집행 시 자동으로 생성·연결된다. */
    public void linkSettlementAccount(User settlementAccount) {
        this.user = settlementAccount;
    }

    @Builder
    public Issuer(String companyName, String bizRegNo, User user) {
        this.companyName = companyName;
        this.bizRegNo = bizRegNo;
        this.user = user;
    }
}
