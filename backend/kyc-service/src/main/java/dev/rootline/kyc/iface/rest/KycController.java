package dev.rootline.kyc.iface.rest;

import static dev.rootline.kyc.iface.rest.Callers.tenant;

import dev.rootline.kyc.application.port.KycPorts.Page;
import dev.rootline.kyc.application.service.KycService;
import dev.rootline.kyc.application.service.KycService.Submission;
import dev.rootline.kyc.application.service.KycService.View;
import dev.rootline.kernel.security.Authenticated;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 實名認證。本人送審 / 查自己的狀態只要登入；審核需要 KYC:REVIEW。 */
@RestController
@RequestMapping("/api/kyc")
class KycController {

    static final String REVIEW = "KYC:REVIEW";

    private final KycService kyc;

    KycController(KycService kyc) {
        this.kyc = kyc;
    }

    record SubmitRequest(@NotBlank String firstName, @NotBlank String lastName, @NotNull LocalDate dateOfBirth,
                         @NotBlank String documentType, @NotBlank String countryCode, @NotBlank String idNumber,
                         @NotNull @Valid AddressRequest address) {

        @Override
        public String toString() {
            return "SubmitRequest[documentType=" + documentType + "]";
        }
    }

    record AddressRequest(@NotBlank String countryCode, String state, @NotBlank String city, String postalCode,
                          @NotBlank String detail) {
    }

    record RejectRequest(@NotBlank String reason) {
    }

    @PostMapping("/submit")
    @Authenticated
    ApiResponse<View> submit(CallerContext caller, @Valid @RequestBody SubmitRequest r) {
        Submission submission = new Submission(r.firstName(), r.lastName(), r.dateOfBirth(), r.documentType(),
                r.countryCode(), r.idNumber(), r.address().countryCode(), r.address().state(), r.address().city(),
                r.address().postalCode(), r.address().detail());
        return ApiResponse.ok("submitted", kyc.submit(tenant(caller), caller.userId(), submission));
    }

    @GetMapping("/me")
    @Authenticated
    ApiResponse<View> me(CallerContext caller) {
        return ApiResponse.ok(kyc.mine(tenant(caller), caller.userId()));
    }

    /** 審核佇列（預設列出待審案件；{@code flaggedOnly=true} 只看有風險標記的）。 */
    @GetMapping("/review-queue")
    @RequiresPermission(REVIEW)
    ApiResponse<Page<View>> queue(CallerContext caller, @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "false") boolean flaggedOnly,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(kyc.queue(tenant(caller), status, flaggedOnly, page, size));
    }

    @PostMapping("/{userId}/approve")
    @RequiresPermission(REVIEW)
    ApiResponse<View> approve(CallerContext caller, @PathVariable String userId) {
        return ApiResponse.ok("approved", kyc.approve(tenant(caller), userId, caller.userId()));
    }

    @PostMapping("/{userId}/reject")
    @RequiresPermission(REVIEW)
    ApiResponse<View> reject(CallerContext caller, @PathVariable String userId,
                             @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok("rejected", kyc.reject(tenant(caller), userId, caller.userId(), request.reason()));
    }
}
