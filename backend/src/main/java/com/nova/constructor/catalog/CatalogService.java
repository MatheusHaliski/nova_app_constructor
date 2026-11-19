package com.nova.constructor.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Carrega o catálogo de tecnologias uma vez, na inicialização. */
@Service
public class CatalogService {

    private final Catalog catalog;

    public CatalogService(JsonMapper mapper) {
        try (InputStream in = new ClassPathResource("catalog/catalog.json").getInputStream()) {
            // No catálogo, campos booleanos ausentes (required, default) valem false.
            this.catalog = mapper.rebuild().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build().readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o catálogo de tecnologias.", e);
        }
    }

    public Catalog get() {
        return catalog;
    }
}
