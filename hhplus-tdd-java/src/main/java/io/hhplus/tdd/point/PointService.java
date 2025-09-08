package io.hhplus.tdd.point;

import java.util.List;

import org.springframework.stereotype.Service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.lock.LockManager;

@Service
public class PointService {
    
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;
    private final LockManager lockManager;

    public PointService(PointHistoryTable pointHistoryTable, UserPointTable userPointTable, LockManager lockManager) {
        this.pointHistoryTable = pointHistoryTable;
        this.userPointTable = userPointTable;
        this.lockManager = lockManager;
    }
    
    // 포인트 조회
    public UserPoint getPoint(long userId) {
        return lockManager.executeWithLock(userId, "GET", () -> {
            UserPoint findUserPoint = userPointTable.selectById(userId);
            if (findUserPoint == null) {
                throw new IllegalArgumentException("유저가 존재하지 않습니다.");
            }
            return findUserPoint;
        });
    }

    // 포인트 이력 조회
    public List<PointHistory> getPointHistory(long userId) {
        return lockManager.executeWithLock(userId, "GET_HISTORY", () -> {
            List<PointHistory> pointHistories = pointHistoryTable.selectAllByUserId(userId);
            if (pointHistories == null) {
                throw new IllegalArgumentException("유저가 존재하지 않습니다.");
            }
            return pointHistories;
        });
    }

    // 포인트 충전
    public UserPoint charge(long userId, long amount) {
        return lockManager.executeWithLock(userId, "CHARGE", () -> {
            // 현재 포인트 조회 후 amount 만틈 더하여 UserPoint update
            UserPoint current = userPointTable.selectById(userId);
            UserPoint chargedUserPoint = current.charge(amount);        
            
            // pointHistory 기록 후 userPoint 업데이트
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(chargedUserPoint.id(), chargedUserPoint.point());
        });
    }

    // 포인트 사용
    public UserPoint use(long userId, long amount) {
        return lockManager.executeWithLock(userId, "USE", () -> {
            // userId로 UserPoint 조회 후 amount 만큼 차감하여 UserPoint update
            UserPoint current = userPointTable.selectById(userId);
            UserPoint usedUserPoint = current.use(amount);

            // pointHistory 기록 후 userPoint 업데이트
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
        });
    }
}
