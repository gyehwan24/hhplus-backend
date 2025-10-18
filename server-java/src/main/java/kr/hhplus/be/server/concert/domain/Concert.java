package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "concerts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String artist;
    private String description;
    private String posterImageUrl;
    private Integer durationMinutes;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Concert(String title, String artist, String description,
                   String posterImageUrl, Integer durationMinutes) {
        this.title = title;
        this.artist = artist;
        this.description = description;
        this.posterImageUrl = posterImageUrl;
        this.durationMinutes = durationMinutes;
    }

    public static Concert create(String title, String artist, String description,
                                String posterImageUrl, Integer durationMinutes) {
        return Concert.builder()
                .title(title)
                .artist(artist)
                .description(description)
                .posterImageUrl(posterImageUrl)
                .durationMinutes(durationMinutes)
                .build();
    }
}
