package kr.hhplus.be.server.concert.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertTest {

    @Test
    @DisplayName("콘서트 생성 성공")
    void create_성공() {
        // given & when
        Concert concert = Concert.builder()
            .title("Coldplay")
            .artist("cris")
            .durationMinutes(180)
            .build();
        // then
        assertThat(concert.getTitle()).isEqualTo("Coldplay");
        assertThat(concert.getArtist()).isEqualTo("cris");
        assertThat(concert.getDurationMinutes()).isEqualTo(180);
    }
}
