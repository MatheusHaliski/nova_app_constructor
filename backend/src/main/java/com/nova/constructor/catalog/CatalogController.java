package com.nova.constructor.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opções do formulário de criação: tudo que o usuário pode escolher vem daqui. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Catalog get() {
        return catalog.get();
    }
}
