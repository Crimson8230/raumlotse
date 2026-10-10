package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.PermissionCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PermissionCodeTest {
    @Test
    void exposesExactlyTheFourteenConfigurableCodesWithoutRoleManagement() {
        assertThat(Arrays.stream(PermissionCode.values()).map(Enum::name).toList())
                .containsExactlyElementsOf(PermissionTestData.PERMISSIONS);
    }
}
