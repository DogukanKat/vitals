package com.example.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Transactional
public class CatalogService {

    private final WebClient webClient;
    private final CatalogRepository repository;

    public CatalogService(WebClient webClient, CatalogRepository repository) {
        this.webClient = webClient;
        this.repository = repository;
    }

    public CatalogEntry refresh(String sku) {
        CatalogPayload payload = webClient
                .get()
                .uri("/catalog/" + sku)
                .retrieve()
                .bodyToMono(CatalogPayload.class)
                .block();
        return repository.upsert(payload.toEntry());
    }

    public CatalogEntry readLocal(String sku) {
        return repository.findBySku(sku).orElseThrow();
    }
}
