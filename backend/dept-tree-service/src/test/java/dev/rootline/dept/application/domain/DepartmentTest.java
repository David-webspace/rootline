package dev.rootline.dept.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rootline.dept.application.domain.dept.Department;
import dev.rootline.dept.application.domain.dept.Department.PendingChanges;
import dev.rootline.dept.application.domain.dept.DepartmentCode;
import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentCreated;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentRestored;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.dept.DepartmentStatus;
import dev.rootline.dept.application.domain.dept.EmployeeId;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartmentTest {

    private static final TenantId TENANT = TenantId.of("northwind");
    private static final String OP = "admin";

    private static Department child(String id, String parent) {
        Department d = Department.create(TENANT, DepartmentId.of(id), DepartmentId.of(parent),
                DepartmentCode.of(id), "Dept " + id, OP);
        d.pullChanges();
        return d;
    }

    private static String code(Throwable e) {
        return ((DomainException) e).code();
    }

    @Test
    void createRaisesCreatedEventAndStartsAtVersionOne() {
        Department d = Department.create(TENANT, DepartmentId.of("eng"), null, DepartmentCode.of("eng"),
                " Engineering ", OP);

        assertThat(d.code().value()).isEqualTo("ENG");
        assertThat(d.name()).isEqualTo("Engineering");
        assertThat(d.status()).isEqualTo(DepartmentStatus.ACTIVE);
        assertThat(d.version()).isEqualTo(1);

        PendingChanges changes = d.pullChanges();
        assertThat(changes.expectedVersion()).isZero();
        assertThat(changes.events()).singleElement().isInstanceOf(DepartmentCreated.class);
    }

    @Test
    void replayingHistoryReproducesTheSameState() {
        List<DepartmentEvent> history = new ArrayList<>();
        Department original = Department.create(TENANT, DepartmentId.of("eng"), DepartmentId.of("hq"),
                DepartmentCode.of("ENG"), "Engineering", OP);
        original.rename("R&D", OP);
        original.moveTo(DepartmentId.of("lab"), OP);
        original.changeSortOrder(3, OP);
        original.assignEmployee(EmployeeId.of("mia"), OP);
        original.assignEmployee(EmployeeId.of("leo"), OP);
        original.unassignEmployee(EmployeeId.of("mia"), OP);
        history.addAll(original.pullChanges().events());

        Department replayed = Department.rehydrate(history);

        assertThat(replayed.name()).isEqualTo("R&D");
        assertThat(replayed.parentId()).isEqualTo(DepartmentId.of("lab"));
        assertThat(replayed.sortOrder()).isEqualTo(3);
        assertThat(replayed.employees()).containsExactly(EmployeeId.of("leo"));
        assertThat(replayed.version()).isEqualTo(original.version()).isEqualTo(7);
    }

    @Test
    void expectedVersionReflectsStoredHistoryNotPendingEvents() {
        Department d = child("eng", "hq");
        d.rename("A", OP);
        d.rename("B", OP);
        PendingChanges changes = d.pullChanges();
        assertThat(changes.expectedVersion()).isEqualTo(1);
        assertThat(changes.events()).hasSize(2);
    }

    @Test
    void noOpChangesProduceNoEvents() {
        Department d = child("eng", "hq");
        d.rename("Dept eng", OP);
        d.moveTo(DepartmentId.of("hq"), OP);
        d.changeSortOrder(0, OP);
        assertThat(d.pullChanges().isEmpty()).isTrue();
    }

    @Test
    void cannotBecomeItsOwnParent() {
        Department d = child("eng", "hq");
        assertThatThrownBy(() -> d.moveTo(DepartmentId.of("eng"), OP))
                .satisfies(e -> assertThat(code(e)).isEqualTo("CYCLE_DETECTED"));
    }

    @Test
    void cannotDisableWhileEmployeesRemain() {
        Department d = child("eng", "hq");
        d.assignEmployee(EmployeeId.of("mia"), OP);
        assertThatThrownBy(() -> d.disable(OP)).satisfies(e -> assertThat(code(e)).isEqualTo("DEPARTMENT_HAS_EMPLOYEES"));

        d.unassignEmployee(EmployeeId.of("mia"), OP);
        d.disable(OP);
        assertThat(d.status()).isEqualTo(DepartmentStatus.DISABLED);
    }

    @Test
    void disabledDepartmentRejectsStructuralChanges() {
        Department d = child("eng", "hq");
        d.disable(OP);
        assertThatThrownBy(() -> d.rename("x", OP)).satisfies(e -> assertThat(code(e)).isEqualTo("DEPARTMENT_DISABLED"));
        assertThatThrownBy(() -> d.moveTo(null, OP)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> d.assignEmployee(EmployeeId.of("mia"), OP)).isInstanceOf(DomainException.class);
    }

    @Test
    void employeeMembershipIsAStrictSet() {
        Department d = child("eng", "hq");
        d.assignEmployee(EmployeeId.of("Mia"), OP);
        assertThatThrownBy(() -> d.assignEmployee(EmployeeId.of("mia"), OP))
                .satisfies(e -> assertThat(code(e)).isEqualTo("EMPLOYEE_ALREADY_ASSIGNED"));
        assertThatThrownBy(() -> d.unassignEmployee(EmployeeId.of("leo"), OP))
                .satisfies(e -> assertThat(code(e)).isEqualTo("EMPLOYEE_NOT_ASSIGNED"));
    }

    @Test
    void rootCannotBeDeletedButChildCan() {
        Department root = Department.create(TENANT, DepartmentId.of("hq"), null, DepartmentCode.of("HQ"), "HQ", OP);
        assertThatThrownBy(() -> root.delete(OP)).satisfies(e -> assertThat(code(e)).isEqualTo("ROOT_NOT_DELETABLE"));

        Department d = child("eng", "hq");
        d.delete(OP);
        assertThat(d.isDeleted()).isTrue();
        assertThatThrownBy(() -> d.rename("x", OP)).satisfies(e -> assertThat(code(e)).isEqualTo("DEPARTMENT_DELETED"));
    }

    @Test
    void restoreReattachesToRootWhenParentIsGone() {
        Department d = child("eng", "hq");
        d.delete(OP);
        d.restore(false, OP);

        assertThat(d.isDeleted()).isFalse();
        assertThat(d.parentId()).isNull();
        assertThat(d.pullChanges().events()).last()
                .isInstanceOfSatisfying(DepartmentRestored.class, e -> assertThat(e.reattachedToRoot()).isTrue());
    }

    @Test
    void everyEventCarriesTheOperator() {
        assertThatThrownBy(() -> Department.create(TENANT, DepartmentId.of("x"), null, DepartmentCode.of("X"), "X", " "))
                .satisfies(e -> assertThat(code(e)).isEqualTo("OPERATOR_REQUIRED"));
    }
}
