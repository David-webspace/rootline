package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.permission.PermissionDefinition;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DomainEventOutbox;
import dev.rootline.dept.application.port.PermissionDefinitionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 權限字典的權威來源。每次異動都寫 outbox，auth-service 依版本投影成自己的權限字典，
 * 角色才能授予這些權限。
 */
@Service
@Transactional
public class PermissionDefinitionService {

    private final PermissionDefinitionRepository repository;
    private final DomainEventOutbox outbox;

    public PermissionDefinitionService(PermissionDefinitionRepository repository, DomainEventOutbox outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    public View define(TenantId tenantId, String systemCode, String permissionCode, String name, String description,
                       String operator) {
        PermissionDefinition definition = PermissionDefinition.define(tenantId, systemCode, permissionCode, name,
                description, operator);
        repository.insert(definition, operator);
        outbox.appendPermissionEvents(definition.pullEvents());
        return View.of(definition);
    }

    public View update(TenantId tenantId, String systemCode, String permissionCode, String name, String description,
                       String operator) {
        PermissionDefinition definition = repository.find(tenantId, systemCode, permissionCode)
                .orElseThrow(() -> DomainException.notFound("PERMISSION_NOT_FOUND",
                        systemCode + ":" + permissionCode + " is not defined"));
        long expected = definition.version();
        definition.update(name, description, operator);
        if (definition.version() != expected) {
            repository.update(definition, expected, operator);
            outbox.appendPermissionEvents(definition.pullEvents());
        }
        return View.of(definition);
    }

    @Transactional(readOnly = true)
    public List<View> list(TenantId tenantId) {
        return repository.findAll(tenantId).stream().map(View::of).toList();
    }

    public record View(String systemCode, String permissionCode, String name, String description, long version) {

        static View of(PermissionDefinition d) {
            return new View(d.systemCode(), d.permissionCode(), d.name(), d.description(), d.version());
        }
    }
}
