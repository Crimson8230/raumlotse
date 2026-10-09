package at.mci.igp.raumlotse.security;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.service.RoleAccessFilter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every mapped protected action must have a server-side authorization rule. */
class AuthorizationMatrixTest {
    @Test void everyMappedEndpointIsClassified() throws Exception {
        var missing = new ArrayList<String>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var definition : scanner.findCandidateComponents("at.mci.igp.raumlotse.controller")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            String prefix = path(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class));
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String route = (prefix + path(mapping)).replaceAll("\\{[^}]+}", "example-id");
                for (var verb : mapping.method()) {
                    String key = verb.name() + " " + route;
                    if (List.of("GET /api/health", "GET /api/auth/csrf", "POST /api/auth/login").contains(key))
                        continue;
                    if (RoleAccessFilter.classify(verb.name(), route) == null) missing.add(key);
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test void unknownAndAlternateMethodsNeverGrantAnAction() {
        assertThat(RoleAccessFilter.classify("POST", "/api/new-action")).isNull();
        assertThat(RoleAccessFilter.classify("HEAD", "/api/buildings")).isEqualTo(
                RoleAccessFilter.classify("GET", "/api/buildings"));
        assertThat(RoleAccessFilter.classify("OPTIONS", "/api/buildings")).isEqualTo(
                RoleAccessFilter.classify("GET", "/api/buildings"));
    }

    private static String path(RequestMapping mapping) {
        return mapping == null || mapping.path().length == 0 ? "" : mapping.path()[0];
    }
}
