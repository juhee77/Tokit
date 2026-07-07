package com.tokit.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "kyc_status", nullable = false)
    private boolean kycStatus; // KYC(신원인증) 통과 여부

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "wallet_address", nullable = false)
    private String walletAddress; // 블록체인 지갑 주소

    @Enumerated(EnumType.STRING)
    @Column(name = "investor_type", nullable = false)
    private InvestorType investorType;

    @Builder
    public User(String name, boolean kycStatus, String email, String walletAddress, InvestorType investorType) {
        this.name = name;
        this.kycStatus = kycStatus;
        this.email = email;
        this.walletAddress = walletAddress;
        this.investorType = investorType != null ? investorType : InvestorType.GENERAL;
    }

    public void updateKycStatus(boolean kycStatus) {
        this.kycStatus = kycStatus;
    }

    public void updateInvestorType(InvestorType investorType) {
        this.investorType = investorType != null ? investorType : InvestorType.GENERAL;
    }
}
