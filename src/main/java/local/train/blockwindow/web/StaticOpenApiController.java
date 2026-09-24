package local.train.blockwindow.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 直接提供随仓库维护的静态 OpenAPI 3 描述文件
 * （/v3/api-docs 是 springdoc 按代码生成的版本，本端点是评审/留档用的手写权威版本）。
 */
@RestController
@Tag(name = "OpenAPI")
public class StaticOpenApiController {

    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    @Operation(summary = "手写 OpenAPI 3 描述（评审/留档版）")
    public ResponseEntity<byte[]> openApiYaml() throws java.io.IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/yaml"))
                .body(new ClassPathResource("openapi/block-window-permit-openapi.yaml")
                        .getContentAsByteArray());
    }
}
