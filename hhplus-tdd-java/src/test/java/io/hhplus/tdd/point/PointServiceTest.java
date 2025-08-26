package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;
    
    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private LockManager lockManager;

    @InjectMocks
    private PointService pointService;

    @BeforeEach
    void setUp() {
        setupLockManagerMock();
    }

    // LockManager Mock 설정을 위한 공통 메서드
    @SuppressWarnings("unchecked")
    private void setupLockManagerMock() {
        // 모든 executeWithLock 호출에 대해 람다를 실행하도록 설정
        when(lockManager.executeWithLock(anyLong(), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return supplier.get();
                });
    }

    /*
     * PATCH /point/{id}/charge : 포인트를 충전한다.
     * - 음수값 충전 시도 
     * - 1회 충전 한도를 넘어서는 포인트 충전 시도
     * - 포인트 충전 성공
     */
    @Nested
    @DisplayName("포인트 충전 테스트")
    class ChargePointTest {
        @Test
        @DisplayName("포인트 충전 성공")
        void charge_정상포인트_성공() {
            // given
            long userId = 1L;
            long chargeAmount = 200;
            long currentPoint = 500;
            long expectedPoint = currentPoint + chargeAmount; // 700

            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            UserPoint updatedUserPoint = new UserPoint(userId, expectedPoint, System.currentTimeMillis());
            PointHistory pointHistory = new PointHistory(updatedUserPoint.id(), userId, chargeAmount,
                    TransactionType.CHARGE, System.currentTimeMillis());

            // Mock 설정
            when(userPointTable.selectById(userId)).thenReturn(userPoint);
            when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(updatedUserPoint);
            when(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
                    .thenReturn(pointHistory);
            
            // when - 실제 동작
            UserPoint result = pointService.charge(userId, chargeAmount);

            // then - 현재 포인트 + 충전 금액 = 결과
            assertThat(result).isNotNull(); // null 체크 추가
            assertThat(result.point()).isEqualTo(updatedUserPoint.point());
            verify(userPointTable).insertOrUpdate(anyLong(), anyLong()); // UserPointTable의 insertOrUpdate 메서드가 호출되었는지
                                                                         // 검증
            verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
        }

        @Test
        @DisplayName("음수 포인트 충전 시 예외 발생")
        void charge_음수포인트_예외발생() {
            // given
            long userId = 1L;
            long chargeAmount = -300L;
            long currentPoint = 200L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.charge(userId, chargeAmount);
            });
        }

        @Test
        @DisplayName("최대 충전 한도를 넘어서는 포인트 충전 시 예외 발생")
        void charge_충전한도초과_예외발생() {
            // given
            long userId = 1L;
            long chargeAmount = 1_000_000L;
            long currentPoint = 100L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.charge(userId, chargeAmount);
            });
        }

        @Test
        @DisplayName("최소 충전 포인트 미만의 포인트 충전 시 예외 발생")
        void charge_최소충전미만_예외발생() {
            // given
            long userId = 1L;
            long chargeAmount = 50L;
            long currentPoint = 100L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.charge(userId, chargeAmount);
            });
        }

        @Test
        @DisplayName("최대 보유 한도를 도달할 시 예외 발생")
        void charge_보유한도초과_예외발생() {
            // given
            long userId = 1L;
            long chargeAmount = 600_000L;
            long currentPoint = 9_500_000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            // mock
            when(userPointTable.selectById(userId)).thenReturn(userPoint);
            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.charge(userId, chargeAmount);
            });
        }
    }
    

    /*
     * PATCH /point/{id}/use : 포인트를 사용한다.
     * - 잔액보다 많은 포인트 사용 시도
     * - 포인트 사용 한도보다 더 큰 포인트 사용 시도
     * - 포인트 사용 성공
     */
    @Nested
    @DisplayName("포인트 사용 테스트")
    class UsePointTest {
        @Test
        @DisplayName("포인트 사용 성공")
        void use_정상포인트_성공() {
            // given
            long userId = 1L;
            long useAmount = 1000L;
            long currentPoint = 2000L;
            long expectedPoint = currentPoint - useAmount;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            UserPoint expectedUserPoint = new UserPoint(userId, expectedPoint, System.currentTimeMillis());
            PointHistory pointHistory = new PointHistory(expectedUserPoint.id(), userId, useAmount, TransactionType.USE,
                    System.currentTimeMillis());

            // Mock 설정
            when(userPointTable.selectById(userId)).thenReturn(userPoint);
            when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(expectedUserPoint);
            when(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
                    .thenReturn(pointHistory);

            // when
            UserPoint result = pointService.use(userId, useAmount);

            // then
            assertThat(result).isNotNull();
            assertThat(result.point()).isEqualTo(expectedUserPoint.point());
            verify(userPointTable).insertOrUpdate(userId, expectedPoint);
            verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
        }

        @Test
        @DisplayName("잔액보다 많은 포인트 사용 시도 시 예외 발생")
        void use_잔액부족_예외발생() {
            // given
            long userId = 1L;
            long useAmount = 1000L;
            long currentPoint = 500L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            // mock 설정
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.use(userId, useAmount);
            });
        }

        @Test
        @DisplayName("최대 사용 포인트 초과 포인트 사용 시도 시 예외 발생")
        void use_최대사용포인트초과_예외발생() {
            // given
            long userId = 1L;
            long useAmount = 1_500_000L;
            long currentPoint = 2_000_000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            // mock 설정
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.use(userId, useAmount);
            });
        }

        @Test
        @DisplayName("최소 사용 포인트 미만 포인트 사용 시도 시 예외 발생")
        void use_최소사용포인트미만_예외발생() {
            // given
            long userId = 1L;
            long useAmount = 90L;
            long currentPoint = 2_000_000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            // mock 설정
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.use(userId, useAmount);
            });
        }
    }
    

    /*
     * GET /point/{id} : 포인트를 조회한다.
     * - id 값 유효성 실패
     * - 포인트 조회 성공
     */
    @Nested
    @DisplayName("포인트 조회 테스트")
    class GetPointTest {
        @Test
        @DisplayName("포인트 조회 성공")
        void getPoint_존재하는유저_성공() {
            // given
            long userId = 1L;
            long currentPoint = 100L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            // mock
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when
            UserPoint findUserPoint = pointService.getPoint(userId);

            // then
            assertThat(findUserPoint).isNotNull();
            assertThat(findUserPoint.point()).isEqualTo(currentPoint);
        }

        @Test
        @DisplayName("존재하지 않는 유저의 포인트 조회 실패")
        void getPoint_존재하지않는유저_예외발생() {
            // given
            long userId = 1L;

            // mock - 의도적으로 null을 조회
            when(userPointTable.selectById(userId)).thenReturn(null);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.getPoint(userId);
            });
        }
    }
    

    /*
     * GET /point/{id}/histories : 포인트 내역을 조회한다.
     * - id 값 유효성 실패
     * - 포인트 내역 조회 성공 
     */
    @Nested
    @DisplayName("포인트 내역 조회 테스트")
    class GetPointHistoryTest {
        @Test
        @DisplayName("포인트 내역 조회 성공")
        void getPointHistory_정상_성공() {
            // given
            long userId = 1L;
            List<PointHistory> pointHistories = List.of(
                    new PointHistory(1L, userId, 100L, TransactionType.USE, System.currentTimeMillis()),
                    new PointHistory(2L, userId, 200L, TransactionType.CHARGE, System.currentTimeMillis()),
                    new PointHistory(3L, userId, 300L, TransactionType.USE, System.currentTimeMillis()),
                    new PointHistory(4L, userId, 400L, TransactionType.CHARGE, System.currentTimeMillis()));
            when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(pointHistories);

            // when
            List<PointHistory> resultList = pointService.getPointHistory(userId);

            // then
            assertThat(resultList).isNotNull();
            assertThat(resultList).isEqualTo(pointHistories);
        }

        @Test
        @DisplayName("존재하지 않는 유저의 포인트 내역 조회 시 예외 발생")
        void getPointHistory_존재하지않는유저_예외발생() {
            // given
            long userId = 1L;

            // mock
            when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(null);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                pointService.getPointHistory(userId);
            });
        }
    }
}