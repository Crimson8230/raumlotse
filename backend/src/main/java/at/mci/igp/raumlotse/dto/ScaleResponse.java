package at.mci.igp.raumlotse.dto;

/** {@code available=false}: no reference segment, no meter values are returned. */
public record ScaleResponse(boolean available, Double pixelsPerMeter, int widthPx, int heightPx) {
}
