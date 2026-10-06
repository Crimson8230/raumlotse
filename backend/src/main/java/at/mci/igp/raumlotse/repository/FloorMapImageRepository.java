package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.FloorMapImage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorMapImageRepository extends JpaRepository<FloorMapImage, UUID> {
}
