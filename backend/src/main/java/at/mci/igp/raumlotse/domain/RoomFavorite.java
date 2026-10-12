package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A room a user marked as personal favorite (feature 017). At most one mark per user and room (primary key) and
 * at most {@code RoomFavoriteService.MAX_FAVORITES} per user, including rooms that are no longer available.
 */
@Entity
@Table(name = "room_favorite")
public class RoomFavorite {

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomFavorite() {
    }

    public RoomFavorite(UUID userId, UUID roomId, Instant createdAt) {
        this.id = new Key(userId, roomId);
        this.createdAt = createdAt;
    }

    public Key getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "room_id")
        private UUID roomId;

        protected Key() {
        }

        public Key(UUID userId, UUID roomId) {
            this.userId = userId;
            this.roomId = roomId;
        }

        public UUID getUserId() {
            return userId;
        }

        public UUID getRoomId() {
            return roomId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(userId, key.userId) && Objects.equals(roomId, key.roomId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roomId);
        }
    }
}
