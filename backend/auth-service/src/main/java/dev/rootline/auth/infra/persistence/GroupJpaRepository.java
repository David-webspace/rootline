package dev.rootline.auth.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupJpaRepository extends JpaRepository<GroupEntity, UUID> {

    Optional<GroupEntity> findByTenantIdAndGroupCode(String tenantId, String groupCode);

    @Query("select g from GroupEntity g join g.members m where g.tenantId = :tenantId and m = :username")
    List<GroupEntity> findByMember(@Param("tenantId") String tenantId, @Param("username") String username);

    List<GroupEntity> findByTenantId(String tenantId);

    boolean existsByTenantIdAndGroupCode(String tenantId, String groupCode);
}
