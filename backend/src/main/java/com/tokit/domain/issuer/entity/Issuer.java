package com.tokit.domain.issuer.entity;

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

    @Builder
    public Issuer(String companyName, String bizRegNo) {
        this.companyName = companyName;
        this.bizRegNo = bizRegNo;
    }
}
