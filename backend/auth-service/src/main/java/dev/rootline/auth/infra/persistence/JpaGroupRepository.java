package dev.rootline.auth.infra.persistence;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.group.GroupCode;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.GroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class JpaGroupRepository implements GroupRepository {

    private final GroupJpaRepository jpa;

    JpaGroupRepository(GroupJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Group> findByCode(TenantId tenantId, GroupCode code) {
        return jpa.findByTenantIdAndGroupCode(tenantId.value(), code.value()).map(JpaGroupRepository::toDomain);
    }

    @Override
    public List<Group> findByMember(TenantId tenantId, Username member) {
        return jpa.findByMember(tenantId.value(), member.value()).stream().map(JpaGroupRepository::toDomain).toList();
    }

    @Override
    public List<Group> findAll(TenantId tenantId) {
        return jpa.findByTenantId(tenantId.value()).stream().map(JpaGroupRepository::toDomain).toList();
    }

    @Override
    public boolean exists(TenantId tenantId, GroupCode code) {
        return jpa.existsByTenantIdAndGroupCode(tenantId.value(), code.value());
    }

    @Override
    public void save(Group group) {
        GroupEntity entity = jpa.findById(group.id()).orElseGet(() -> new GroupEntity(group.id()));
        entity.tenantId = group.tenantId().value();
        entity.groupCode = group.code().value();
        entity.groupName = group.name();
        entity.members.clear();
        group.members().forEach(m -> entity.members.add(m.value()));
        entity.roles.clear();
        group.roles().forEach(r -> entity.roles.add(r.value()));
        jpa.save(entity);
    }

    private static Group toDomain(GroupEntity e) {
        return Group.rehydrate(e.id, TenantId.of(e.tenantId), GroupCode.of(e.groupCode), e.groupName,
                e.members.stream().map(Username::of).collect(Collectors.toSet()),
                e.roles.stream().map(RoleCode::of).collect(Collectors.toSet()));
    }
}
