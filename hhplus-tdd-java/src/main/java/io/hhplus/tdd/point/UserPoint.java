package io.hhplus.tdd.point;

public record UserPoint(
        long id,
        long point,
        long updateMillis
) {
    public UserPoint {
        if (point < 0) {
            throw new IllegalArgumentException("포인트는 음수일 수 없습니다.");
        }
        if (point > MAX_BALANCE) {
            throw new IllegalArgumentException("최대 보유 한도 초과");
        }
    }
    // 1회 충전 한도
    public static final long MAX_CHARGE_POINT = 1_000_000L;
    // 최소 충전 포인트
    public static final long MIN_CHARGE_POINT = 100L;
    // 최소 사용 포인트
    public static final long MIN_USE_POINT = 100L;
    // 최대 보유 한도
    public static final long MAX_BALANCE = 10_000_000L;

    public UserPoint charge (long point) {
        validateChargePoint(point);
        long newBalance = this.point + point;
        return new UserPoint(this.id, newBalance, System.currentTimeMillis());
    } 
    

    public static void validateChargePoint(long point) {
        if (point < 0L) {
            throw new IllegalArgumentException("충전 금액은 양수여야 합니다.");
        } 
        if (point >= MAX_CHARGE_POINT) {
            throw new IllegalArgumentException("1회 최대 충전 한도 포인트는 1,000,000 포인트입니다.");
        }
        if (point < MIN_CHARGE_POINT) {
            throw new IllegalArgumentException("최소 충전 포인트는 100 포인트입니다.");
        }
    }

    public static UserPoint empty(long id) {
        return new UserPoint(id, 0, System.currentTimeMillis());
    }   
}
