package kr.hhplus.be.server.concert.application.response;

import kr.hhplus.be.server.concert.domain.Concert;

import java.io.Serializable;

/**
 * 콘서트 응답 DTO
 */
public record ConcertResponse(
    Long id,
    String title,
    String artist,
    String description,
    String posterImageUrl,
    Integer durationMinutes
) implements Serializable {
    /**
     * 도메인 객체를 DTO로 변환
     *
     * @param concert 콘서트 도메인 객체
     * @return 콘서트 응답 DTO
     */
    public static ConcertResponse from(Concert concert) {
        return new ConcertResponse(
            concert.getId(),
            concert.getTitle(),
            concert.getArtist(),
            concert.getDescription(),
            concert.getPosterImageUrl(),
            concert.getDurationMinutes()
        );
    }
}
