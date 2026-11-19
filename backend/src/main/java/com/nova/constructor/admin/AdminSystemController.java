package com.nova.constructor.admin;

import com.nova.constructor.config.NovaProperties;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Visão das fontes de dados: MySQL central e conectores opcionais (somente ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    public record DataSource(String id, String name, String role, boolean required, boolean enabled, String purpose) {}

    private final NovaProperties props;

    public AdminSystemController(NovaProperties props) {
        this.props = props;
    }

    @GetMapping("/data-sources")
    public List<DataSource> dataSources() {
        NovaProperties.Connectors c = props.connectors();
        return List.of(
                new DataSource("mysql", "MySQL", "central", true, true, "Fonte central: usuários, projetos, versões e consentimentos"),
                new DataSource("redis", "Redis", "opcional", false, c.redis().enabled(), "Cotas, contadores e limites de requisição"),
                new DataSource("opensearch", "OpenSearch", "opcional", false, c.opensearch().enabled(), "Busca de projetos e tecnologias"),
                new DataSource("s3", "S3 / MinIO", "opcional", false, c.s3().enabled(), "Mídia e pacotes gerados"),
                new DataSource("cassandra", "Cassandra", "opcional", false, c.cassandra().enabled(), "Timeline e notificações com TTL"));
    }
}
