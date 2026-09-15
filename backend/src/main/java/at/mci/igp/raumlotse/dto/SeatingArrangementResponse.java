package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.SeatingArrangement;
import java.util.UUID;

public record SeatingArrangementResponse(UUID id, String name, int maxCapacity) {

    public static SeatingArrangementResponse from(SeatingArrangement seatingArrangement) {
        return new SeatingArrangementResponse(
                seatingArrangement.getId(), seatingArrangement.getName(), seatingArrangement.getMaxCapacity());
    }
}
