package com.tokit.domain.user.entity;

/**
 * 계정 권한. 자산 등록·배당 실행·대사 조회처럼 운영자만 수행해야 하는 작업을 구분합니다.
 */
public enum Role {
    USER,
    ADMIN
}
