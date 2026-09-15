package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.domain.EntityStatus;

final class StatusFilter {

    private StatusFilter() {
    }

    static EntityStatus parse(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
            return null;
        }
        return EntityStatus.valueOf(raw.toUpperCase());
    }
}
