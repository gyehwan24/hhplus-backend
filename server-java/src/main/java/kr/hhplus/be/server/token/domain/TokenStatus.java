package kr.hhplus.be.server.token.domain;

public enum TokenStatus {
    WAITING,    // 대기 중
    ACTIVE,     // 활성화 (예약 가능)
    EXPIRED     // 만료됨
}
