package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.SeatingArrangement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatingArrangementRepository extends JpaRepository<SeatingArrangement, UUID> {
}
