package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    public PointController(PointHistoryTable pointHistoryTable, UserPointTable userPointTable) {
        this.pointHistoryTable = pointHistoryTable;
        this.userPointTable = userPointTable;
    }

    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point(
            @PathVariable long id 
    ) {
        UserPoint userPoint = userPointTable.selectById(id);
        log.info("UserPoint 조회: {} / {}", userPoint.id(), userPoint.point());
        return userPoint;
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(id);
        log.info("UserPoint History 조회: {} / {}", id, histories.size());
        return histories;
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // 기존 포인트를 구하여 업데이트, 없으면 새로 충전
        long remainPoint = userPointTable.selectById(id).point();
        UserPoint userPoint = userPointTable.insertOrUpdate(id, remainPoint + amount);
        log.info("UserPoint 충전 완료: {} / {}", userPoint.id(), userPoint.point());
        PointHistory pointHistory = pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());
        log.info("PointHistory 추가 완료: {} / {}", pointHistory.userId(), pointHistory.amount());
        return userPoint;
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    // TODO: amount값 유효성 검증 추가 / 업데이트 한 값이 아닌 이전 userPoint를 반환중 / 동시성문제 / 트랜잭션 처리
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // UserPointTable에서 유저의 잔여 포인트를 조회하여 amount를 뺀후 업데이트
        // 사용 기록을 PointHistoryTable에 추가
        UserPoint userPoint = userPointTable.selectById(id);
        // 잔여 포인트가 amount보다 작으면 예외 처리
        if (userPoint.point() < amount) {
            log.error("포인트 부족: 유저 ID = {}, 요청 포인트 = {}, 잔여 포인트 = {}", id, amount, userPoint.point());
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        UserPoint updateUserPoint = userPointTable.insertOrUpdate(id, userPoint.point() - amount);
        log.info("UserPoint 사용 완료: {} / {}", updateUserPoint.id(), updateUserPoint.point());
        PointHistory pointHistory = pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());
        log.info("PointHistory 추가 완료: {} / {}", pointHistory.userId(), pointHistory.amount());
        return updateUserPoint;
    }
}
