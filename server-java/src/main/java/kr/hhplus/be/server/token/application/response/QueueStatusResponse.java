package kr.hhplus.be.server.token.application.response;

/**
 * 대기열 상태 응답 DTO
 */
public record QueueStatusResponse(
    String status,      // WAITING, ACTIVE, NOT_IN_QUEUE
    long position       // 대기열 순번 (WAITING일 때만 유효)
) {
    public static QueueStatusResponse waiting(long position) {
        return new QueueStatusResponse("WAITING", position);
    }

    public static QueueStatusResponse active() {
        return new QueueStatusResponse("ACTIVE", 0);
    }

    public static QueueStatusResponse notInQueue() {
        return new QueueStatusResponse("NOT_IN_QUEUE", 0);
    }
}
