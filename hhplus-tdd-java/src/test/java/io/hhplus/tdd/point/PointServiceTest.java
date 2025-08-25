package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;
    
    @Mock
    private PointHistoryTable pointHistoryTable;
    
    // @InjectMocks
    // private PointController pointController;

    @InjectMocks
    private PointService pointService;

    /*
     * PATCH /point/{id}/charge : 포인트를 충전한다.
     * - 음수값 충전 시도 
     * - 1회 충전 한도를 넘어서는 포인트 충전 시도
     * - 포인트 충전 성공
     */
    @Test
    @DisplayName("포인트 충전 성공")
    void charge_정상포인트_성공() {
        // given
        long userId = 1L;
        long chargeAmount = 200;
        long currentPoint = 500;
        long expectedPoint = currentPoint + chargeAmount;  // 700
        
        UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, expectedPoint, System.currentTimeMillis());
        PointHistory pointHistory = new PointHistory(updatedUserPoint.id(), userId, chargeAmount, TransactionType.CHARGE, System.currentTimeMillis());

        // Mock 설정 
        when(userPointTable.selectById(userId)).thenReturn(userPoint);
        when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(updatedUserPoint); 
        when(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong())).thenReturn(pointHistory);

        // when - 실제 동작
        UserPoint result = pointService.charge(userId, chargeAmount);

        // then - 현재 포인트 + 충전 금액 = 결과
        assertThat(result).isNotNull();  // null 체크 추가
        assertThat(result.point()).isEqualTo(expectedPoint);
        verify(userPointTable).insertOrUpdate(anyLong(), anyLong()); // UserPointTable의 insertOrUpdate 메서드가 호출되었는지 검증
        verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("음수 포인트 충전 시 예외 발생")
    void charge_음수포인트_예외발생() {
        // given
        long userId = 1L;
        long chargeAmount = -300L;
        long currentPoint = 200L;
        UserPoint userPoint = new UserPoint (userId, currentPoint, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            pointService.charge(userId, chargeAmount);
        });
    }

    @Test
    @DisplayName("최대 충전 한도를 넘어서는 포인트 충전 시 예외 발생")
    void charge_한도초과포인트_예외발생() {
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

    
    /*
     * PATCH /point/{id}/use : 포인트를 사용한다.
     * - 잔액보다 많은 포인트 사용 시도
     * - 음수값 사용 시도
     * - 포인트 사용 한도보다 더 큰 포인트 사용 시도
     * - 포인트 사용 성공
     */

    /*
     * GET /point/{id} : 포인트를 조회한다.
     * - id 값 유효성 실패
     * - 포인트 조회 성공
     */

    /*
     * GET /point/{id}/histories : 포인트 내역을 조회한다.
     * - id 값 유효성 실패
     * - 포인트 내역 조회 성공 
     */
    
    // @Test
    // @DisplayName("🔴 RED: 실패 케이스 - 틀린 값을 기대")
    // void 실패하는_테스트_예시() {
    //     // given
    //     long userId = 1L;
    //     UserPoint mockUserPoint = new UserPoint(userId, 500L, System.currentTimeMillis());
    //     when(userPointTable.selectById(userId)).thenReturn(mockUserPoint);

    //     // when
    //     UserPoint result = pointController.point(userId);

    //     // then - 일부러 틀린 값(1000L)을 기대하여 실패하도록 만듦
    //     assertThat(result.point()).isEqualTo(1000L); 
    // }

    // @Test
    // @DisplayName("특정 유저의 포인트를 조회할 수 있다")
    // void 포인트_조회_성공() {
    //     // given (준비) - 테스트에 필요한 데이터와 상황 설정
    //     long userId = 1L;
    //     long expectedPoint = 1000L;
    //     UserPoint mockUserPoint = new UserPoint(userId, expectedPoint, System.currentTimeMillis());
        
    //     // Mock 동작 정의: userId로 조회 시 mockUserPoint 반환
    //     when(userPointTable.selectById(userId)).thenReturn(mockUserPoint);
        
    //     // when (실행) - 실제 테스트하려는 메서드 호출
    //     UserPoint result = pointController.point(userId);
        
    //     // then (검증) - 결과가 예상과 같은지 확인
    //     assertThat(result).isNotNull();
    //     assertThat(result.id()).isEqualTo(userId);
    //     assertThat(result.point()).isEqualTo(expectedPoint);
    // }
    
    // @Test
    // @DisplayName("존재하지 않는 유저의 포인트 조회시 기본값을 반환한다")
    // void 포인트_조회_유저없음() {
    //     // given
    //     long userId = 999L;
    //     UserPoint emptyUserPoint = UserPoint.empty(userId);
        
    //     when(userPointTable.selectById(userId)).thenReturn(emptyUserPoint);
        
    //     // when
    //     UserPoint result = pointController.point(userId);
        
    //     // then
    //     assertThat(result.id()).isEqualTo(userId);
    //     assertThat(result.point()).isEqualTo(0L);
    // }

    
    // @Test
    // @DisplayName("포인트를 충전할 수 있다")
    // void 포인트_충전_성공() {
    //     // given - 초기 상태 설정
    //     long userId = 1L;
    //     long currentPoint = 1000L;
    //     long chargeAmount = 500L;
    //     long expectedPoint = currentPoint + chargeAmount;  // 1500L
        
    //     // 현재 포인트 조회 시 Mock 동작
    //     UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
    //     when(userPointTable.selectById(userId)).thenReturn(currentUserPoint);
        
    //     // 포인트 업데이트 시 Mock 동작
    //     UserPoint updatedUserPoint = new UserPoint(userId, expectedPoint, System.currentTimeMillis());
    //     when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(updatedUserPoint);
        
    //     // 히스토리 저장 시 Mock 동작 - 모든 파라미터에 matcher 사용
    //     PointHistory mockHistory = new PointHistory(1L, userId, chargeAmount, TransactionType.CHARGE, System.currentTimeMillis());
    //     when(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
    //         .thenReturn(mockHistory);
        
    //     // when - 충전 실행
    //     UserPoint result = pointController.charge(userId, chargeAmount);
        
    //     // then - 결과 검증
    //     assertThat(result.point()).isEqualTo(expectedPoint);
    //     assertThat(result.id()).isEqualTo(userId);
    // }
    
    // @Test
    // @DisplayName("포인트를 사용할 수 있다")
    // void 포인트_사용_성공() {
    //     // given
    //     long userId = 1L;
    //     long currentPoint = 1000L;
    //     long useAmount = 300L;
    //     long expectedPoint = currentPoint - useAmount;  // 700L
        
    //     // 현재 포인트 조회
    //     UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
    //     when(userPointTable.selectById(userId)).thenReturn(currentUserPoint);
        
    //     // 포인트 차감 후 업데이트
    //     UserPoint updatedUserPoint = new UserPoint(userId, expectedPoint, System.currentTimeMillis());
    //     when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(updatedUserPoint);
        
    //     // 히스토리 저장
    //     PointHistory mockHistory = new PointHistory(1L, userId, useAmount, TransactionType.USE, System.currentTimeMillis());
    //     when(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
    //         .thenReturn(mockHistory);
        
    //     // when
    //     UserPoint result = pointController.use(userId, useAmount);
        
    //     // then
    //     assertThat(result.point()).isEqualTo(expectedPoint);
    //     assertThat(result.id()).isEqualTo(userId);
    // }
    
    // @Test
    // @DisplayName("포인트가 부족하면 예외가 발생한다")
    // void 포인트_사용_실패_잔액부족() {
    //     // given
    //     long userId = 1L;
    //     long currentPoint = 100L;
    //     long useAmount = 500L;  // 잔액보다 큰 금액
        
    //     UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
    //     when(userPointTable.selectById(userId)).thenReturn(currentUserPoint);
        
    //     // when & then - 예외 발생 검증
    //     org.assertj.core.api.Assertions.assertThatThrownBy(() -> 
    //         pointController.use(userId, useAmount)
    //     )
    //     .isInstanceOf(IllegalArgumentException.class)
    //     .hasMessage("포인트가 부족합니다.");
    // }
}