package io.hhplus.tdd.point;

import org.springframework.stereotype.Service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

@Service
public class PointService {
    
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    public PointService(PointHistoryTable pointHistoryTable, UserPointTable userPointTable) {
        this.pointHistoryTable = pointHistoryTable;
        this.userPointTable = userPointTable;
    }
    
    public UserPoint charge(long userId, long amount) {
        // 현재 포인트 조회
        UserPoint current = userPointTable.selectById(userId);
        UserPoint chargedUserPoint = current.charge(amount);        
        
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
        return userPointTable.insertOrUpdate(chargedUserPoint.id(), chargedUserPoint.point());
    }
}
