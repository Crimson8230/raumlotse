package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.exception.MapRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Verifies map uploads by decoding their header instead of trusting file name or client content type.
 * Only PNG and JPEG are accepted (SVG can carry active content).
 */
@Component
public class MapImageValidator {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 50_000_000L;
    private static final Logger log = LoggerFactory.getLogger(MapImageValidator.class);

    public record ValidatedImage(String contentType, int widthPx, int heightPx) {
    }

    public ValidatedImage validate(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            log.warn("map_image_rejected code=MAP_IMAGE_TOO_LARGE");
            throw new MapRequestException(HttpStatus.CONTENT_TOO_LARGE, "MAP_IMAGE_TOO_LARGE",
                    "Das Bild ist größer als 10 MB.");
        }
        if (bytes.length == 0) {
            throw unsupported();
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                String contentType = contentTypeOf(reader.getFormatName());
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw unsupported();
                }
                return new ValidatedImage(contentType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof MapRequestException mapEx) {
                throw mapEx;
            }
            throw unsupported();
        }
    }

    private static String contentTypeOf(String formatName) {
        return switch (formatName.toLowerCase()) {
            case "png" -> "image/png";
            case "jpeg", "jpg" -> "image/jpeg";
            default -> throw unsupported();
        };
    }

    private static MapRequestException unsupported() {
        log.warn("map_image_rejected code=MAP_IMAGE_UNSUPPORTED");
        return new MapRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MAP_IMAGE_UNSUPPORTED",
                "Es werden nur PNG- und JPEG-Bilder unterstützt.");
    }
}
