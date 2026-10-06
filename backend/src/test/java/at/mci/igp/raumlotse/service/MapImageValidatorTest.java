package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.exception.MapRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MapImageValidatorTest {

    private final MapImageValidator validator = new MapImageValidator();

    static byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void acceptsPngAndReadsDimensions() throws Exception {
        var result = validator.validate(image("png", 120, 80));
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.widthPx()).isEqualTo(120);
        assertThat(result.heightPx()).isEqualTo(80);
    }

    @Test
    void acceptsJpeg() throws Exception {
        var result = validator.validate(image("jpg", 64, 32));
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.widthPx()).isEqualTo(64);
    }

    @Test
    void rejectsSvgTextAndEmptyContent() {
        for (byte[] bytes : new byte[][] {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(), "hello".getBytes(), new byte[0] }) {
            assertThatThrownBy(() -> validator.validate(bytes))
                    .isInstanceOfSatisfying(MapRequestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                        assertThat(ex.getCode()).isEqualTo("MAP_IMAGE_UNSUPPORTED");
                    });
        }
    }

    @Test
    void rejectsOtherImageFormatsEvenWithValidContent() throws Exception {
        assertThatThrownBy(() -> validator.validate(image("gif", 10, 10)))
                .isInstanceOf(MapRequestException.class);
    }

    @Test
    void rejectsCorruptPngBody() {
        byte[] bogus = new byte[64];
        bogus[0] = (byte) 0x89;
        bogus[1] = 'P';
        bogus[2] = 'N';
        bogus[3] = 'G';
        assertThatThrownBy(() -> validator.validate(bogus)).isInstanceOf(MapRequestException.class);
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() {
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> validator.validate(tooLarge))
                .isInstanceOfSatisfying(MapRequestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
                    assertThat(ex.getCode()).isEqualTo("MAP_IMAGE_TOO_LARGE");
                });
    }
}
