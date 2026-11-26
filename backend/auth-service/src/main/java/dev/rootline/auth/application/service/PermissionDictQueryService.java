package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.shared.Views.PermissionDictEntryView;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PermissionDictQueryService {

    private final PermissionDictionary dictionary;

    public PermissionDictQueryService(PermissionDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public List<PermissionDictEntryView> list(TenantId tenantId) {
        return dictionary.findAll(tenantId).stream()
                .sorted(Comparator.comparing(d -> d.key().authority()))
                .map(d -> new PermissionDictEntryView(d.key().systemCode(), d.key().permissionCode(), d.name(),
                        d.description(), d.version()))
                .toList();
    }
}
