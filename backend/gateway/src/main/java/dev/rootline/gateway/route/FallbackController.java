package dev.rootline.gateway.route;

import dev.rootline.gateway.web.GatewayResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 熔斷或下游無回應時的降級回應。格式與各服務的 ApiResponse 相同，前端只需處理一種錯誤格式。 */
@RestController
class FallbackController {

    @RequestMapping("/fallback/{service}")
    ResponseEntity<Map<String, Object>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(GatewayResponses.body("SERVICE_UNAVAILABLE",
                        "The " + service + " service is temporarily unavailable; please retry shortly"));
    }
}
