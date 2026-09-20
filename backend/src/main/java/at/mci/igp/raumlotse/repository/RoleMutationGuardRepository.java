package at.mci.igp.raumlotse.repository;
import at.mci.igp.raumlotse.domain.RoleMutationGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface RoleMutationGuardRepository extends JpaRepository<RoleMutationGuard,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from RoleMutationGuard g where g.id = 1")
    RoleMutationGuard lockGuard();
}

