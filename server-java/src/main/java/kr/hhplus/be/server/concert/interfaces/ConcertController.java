package kr.hhplus.be.server.concert.interfaces;

import kr.hhplus.be.server.concert.application.ConcertService;
import kr.hhplus.be.server.concert.application.response.ConcertResponse;
import kr.hhplus.be.server.concert.application.response.ConcertScheduleResponse;
import kr.hhplus.be.server.concert.application.response.SeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    // 시나리오 1: 콘서트 목록 조회 (캐시 TTL 5분)
    @GetMapping("/concerts")
    public ResponseEntity<List<ConcertResponse>> getAllConcerts() {
        return ResponseEntity.ok(concertService.getAllConcerts());
    }

    // 시나리오 2: 콘서트 상세 조회 (캐시 TTL 10분)
    @GetMapping("/concerts/{concertId}")
    public ResponseEntity<ConcertResponse> getConcert(@PathVariable Long concertId) {
        return ResponseEntity.ok(concertService.getConcertById(concertId));
    }

    // 시나리오 3: 스케줄 조회 (캐시 TTL 3분)
    @GetMapping("/concerts/{concertId}/schedules")
    public ResponseEntity<List<ConcertScheduleResponse>> getSchedules(
            @PathVariable Long concertId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LocalDate from = fromDate != null ? fromDate : LocalDate.now();
        LocalDate to = toDate != null ? toDate : LocalDate.now().plusMonths(1);
        return ResponseEntity.ok(concertService.getAvailableSchedules(concertId, from, to));
    }

    // 시나리오 4: 좌석 조회 (캐시 TTL 10초) - 핵심 테스트
    @GetMapping("/schedules/{scheduleId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeats(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(concertService.getAvailableSeats(scheduleId));
    }
}
