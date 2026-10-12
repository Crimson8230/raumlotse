package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomFavoriteRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.RoomFavoriteService;
import at.mci.igp.raumlotse.service.RoomSearchService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** Feature 017, Constitution IV: favorite and search log lines carry counts only, never user or room ids. */
class RoomRankingLogPrivacyTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private final RoomFavoriteRepository favorites = mock(RoomFavoriteRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomFavoriteService favoriteService = new RoomFavoriteService(favorites, rooms,
            mock(UserAccountRepository.class), Clock.systemUTC());

    private final UUID userId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    @BeforeEach
    void attach() {
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        Room room = new Room("Geheimer Raum", new Floor(new Building("Haus 1"), "EG"));
        ReflectionTestUtils.setField(room, "id", roomId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(rooms.findSearchCandidates()).thenReturn(List.of(room));
        appender.list.clear();
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
    }

    private void assertNoIdentifiersLogged() {
        List<ILoggingEvent> own = appender.list.stream()
                .filter(event -> event.getLoggerName().startsWith("at.mci.igp.raumlotse"))
                .toList();
        assertThat(own).isNotEmpty();
        for (ILoggingEvent event : own) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain(userId.toString())
                    .doesNotContain(roomId.toString())
                    .doesNotContain("Geheimer Raum");
        }
    }

    @Test
    void markingAndReachingTheLimitLogOnlyCounts() {
        when(favorites.countByIdUserId(userId)).thenReturn(3L, 10L);

        favoriteService.mark(userId, roomId);
        assertThatThrownBy(() -> favoriteService.mark(userId, roomId)).isInstanceOf(ConflictException.class);

        assertNoIdentifiersLogged();
    }

    @Test
    void rankedSearchLogsOnlyCountsAndFlags() {
        when(favorites.findRoomIdsByUserId(any())).thenReturn(Set.of(roomId));
        RoomSearchService search = new RoomSearchService(rooms, mock(ReservationRepository.class), favoriteService,
                Clock.systemUTC());

        search.search(new RoomSearchCriteria(null, null, null, null, Set.of(), false, null, null, true), userId);

        assertNoIdentifiersLogged();
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("room_search result_count=1 favorites_only=true favorites_count=1");
    }
}
