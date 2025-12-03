package kr.hhplus.be.server.token.interfaces;

import kr.hhplus.be.server.token.application.TokenService;
import kr.hhplus.be.server.token.application.request.IssueTokenRequest;
import kr.hhplus.be.server.token.application.response.IssueTokenResponse;
import kr.hhplus.be.server.token.application.response.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 대기열/토큰 API Controller
 */
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    /**
     * 대기열 진입 (토큰 발급)
     *
     * @param request 요청 (userId)
     * @return 대기열 순번
     */
    @PostMapping("/token")
    public ResponseEntity<IssueTokenResponse> issueToken(@RequestBody IssueTokenRequest request) {
        long position = tokenService.issueToken(request.userId());
        return ResponseEntity.ok(new IssueTokenResponse(request.userId(), position));
    }

    /**
     * 대기열/활성 상태 조회
     *
     * @param userId 사용자 ID
     * @return 상태 정보 (WAITING/ACTIVE/NOT_IN_QUEUE)
     */
    @GetMapping("/status")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(@RequestParam Long userId) {
        QueueStatusResponse response = tokenService.getQueueStatus(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 대기열 순번 조회
     *
     * @param userId 사용자 ID
     * @return 대기열 순번 (1부터 시작, 대기열에 없으면 0)
     */
    @GetMapping("/position")
    public ResponseEntity<PositionResponse> getQueuePosition(@RequestParam Long userId) {
        long position = tokenService.getQueuePosition(userId);
        return ResponseEntity.ok(new PositionResponse(userId, position));
    }

    public record PositionResponse(Long userId, long position) {}
}
