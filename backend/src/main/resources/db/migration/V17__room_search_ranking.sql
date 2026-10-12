-- Feature 017: ranked room search.
-- Specialised equipment counts against a room when not requested; existing types start as general.
ALTER TABLE equipment_type ADD COLUMN specialised BOOLEAN NOT NULL DEFAULT false;

-- Personal favorite rooms (at most 10 per user, enforced in RoomFavoriteService).
CREATE TABLE room_favorite (
    user_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, room_id)
);

CREATE INDEX ix_room_favorite_room_id ON room_favorite (room_id);
