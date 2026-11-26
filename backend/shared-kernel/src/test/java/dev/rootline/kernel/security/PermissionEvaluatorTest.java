package dev.rootline.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionEvaluatorTest {

    @Test
    void exactPermissionIsGranted() {
        assertThat(PermissionEvaluator.isGranted(Set.of("IAM:USER_READ"), "IAM:USER_READ")).isTrue();
    }

    @Test
    void otherPermissionIsNotGranted() {
        assertThat(PermissionEvaluator.isGranted(Set.of("IAM:USER_READ"), "IAM:USER_WRITE")).isFalse();
    }

    @Test
    void superWildcardGrantsEverything() {
        assertThat(PermissionEvaluator.isGranted(Set.of("*:ADMIN_ALL"), "DEPT:TREE_WRITE")).isTrue();
    }

    @Test
    void systemWildcardOnlyGrantsItsOwnSystem() {
        Set<String> granted = Set.of("IAM:ADMIN_ALL");
        assertThat(PermissionEvaluator.isGranted(granted, "IAM:ROLE_WRITE")).isTrue();
        assertThat(PermissionEvaluator.isGranted(granted, "DEPT:TREE_WRITE")).isFalse();
    }

    @Test
    void noRequirementIsAlwaysGranted() {
        assertThat(PermissionEvaluator.isGranted(Set.of(), null)).isTrue();
    }

    @Test
    void emptyGrantsDenyRequirement() {
        assertThat(PermissionEvaluator.isGranted(Set.of(), "IAM:USER_READ")).isFalse();
    }
}
