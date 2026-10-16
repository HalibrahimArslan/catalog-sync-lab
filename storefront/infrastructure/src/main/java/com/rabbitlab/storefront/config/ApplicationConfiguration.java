package com.rabbitlab.storefront.config;

import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.ProcessedUpdates;
import com.rabbitlab.storefront.application.Transaction;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application servisleri framework'süz; burada bean yapılıyor. */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    CatalogService catalogService(CatalogRepository repository, ProcessedUpdates processed, Transaction transaction) {
        return new CatalogService(repository, processed, transaction);
    }
}
