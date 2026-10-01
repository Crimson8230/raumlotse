package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotNull;

public record RoomDeviceCommandRequest(@NotNull Boolean state) { }
