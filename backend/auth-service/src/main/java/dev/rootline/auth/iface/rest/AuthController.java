package dev.rootline.auth.iface.rest;

import dev.rootline.auth.application.service.AuthCommandService;
import dev.rootline.auth.application.shared.Views.LoginResult;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公開 endpoint：登入、註冊、JWKS。Gateway 對這些路徑不要求 JWT。 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthCommandService auth;

    AuthController(AuthCommandService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    ApiResponse<LoginResult> login(@Valid @RequestBody Requests.Login request) {
        return ApiResponse.ok(auth.login(request.tenantCode(), request.username(), request.password()));
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody Requests.Register request) {
        String username = auth.register(request.tenantCode(), request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("registered", username));
    }

    /** Gateway 以此取得驗簽公鑰；回傳標準 JWKS 格式，不包 ApiResponse。 */
    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .body(auth.publicJwks());
    }
}
