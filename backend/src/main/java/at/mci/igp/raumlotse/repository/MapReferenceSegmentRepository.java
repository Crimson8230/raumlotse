package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.MapReferenceSegment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapReferenceSegmentRepository extends JpaRepository<MapReferenceSegment, UUID> {
}
