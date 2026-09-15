package at.mci.igp.raumlotse.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * No feature currently references rooms (see spec.md Assumptions), so this always answers
 * {@code false}. Replace/extend once a feature (e.g. bookings) adds real dependent history.
 */
@Component
public class NoDependentHistoryChecker implements RoomDependentHistoryChecker {

    @Override
    public boolean hasDependentHistory(UUID roomId) {
        return false;
    }
}
