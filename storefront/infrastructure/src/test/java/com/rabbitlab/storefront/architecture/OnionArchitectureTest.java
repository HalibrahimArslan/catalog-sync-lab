package com.rabbitlab.storefront.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * P3-M5 — Onion kuralları. Bu test, context'in tüm halkalarını (bu modülün classpath'indeki
 * storefront-model, -domain-service, -application jar'ları dahil) birlikte tarar.
 *
 * <pre>
 *   ┌──────────────── infrastructure (web, persistence, messaging, config) ────────────────┐
 *   │   ┌──────────── application ─────────────┐                                       │
 *   │   │   ┌──── domain-service ────┐          │                                       │
 *   │   │   │   ┌── model ──┐        │          │                                       │
 *   │   │   │   └───────────┘        │          │                                       │
 *   │   │   └────────────────────────┘          │                                       │
 *   │   └───────────────────────────────────────┘                                       │
 *   └──────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Hangi kuralı kim koruyor?
 * <ul>
 *   <li><b>Derleyici</b> (halkalar ayrı Maven modülü): iç halka dış halkayı göremez; model, domain-service ve
 *       application Spring'i, JDBC'yi, RabbitMQ'yu, mesaj sözleşmesini göremez (classpath'lerinde yoklar).
 *       Faz 2'de bunların her biri burada ayrı bir ArchUnit kuralıydı.</li>
 *   <li><b>ArchUnit</b> (bu test): derleyicinin göremedikleri. Aynı modüldeki adapter'lar birbirine
 *       bağımlı olmamalı; web domain modelini görmemeli; kimse wiring'e (config) bağımlı olmamalı.
 *       {@code onionArchitecture()} halka yönünü de tekrar doğrular: modül yapısı bir gün birleştirilirse
 *       güvence kaybolmaz.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.rabbitlab.storefront", importOptions = ImportOption.DoNotIncludeTests.class)
class OnionArchitectureTest {

    @ArchTest
    static final ArchRule onion = onionArchitecture()
            .domainModels("..storefront.domain..")
            .domainServices("..storefront.domainservice..")
            .applicationServices("..storefront.application..")
            .adapter("web", "..storefront.adapter.in.web..")
            .adapter("messaging", "..storefront.adapter.in.messaging..")
            .adapter("persistence", "..storefront.adapter.out.persistence..")
            // config, her şeyi birbirine bağlayan composition root; tanımı gereği tüm halkaları bilir.
            .ignoreDependency(resideInAPackage("..storefront.config.."), alwaysTrue())
            .because("her halka sadece içindekileri bilir; adapter'lar birbirini tanımaz");

    @ArchTest
    static final ArchRule web_never_sees_domain_model = noClasses()
            .that().resideInAPackage("..storefront.adapter.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..storefront.domain..")
            .because("vitrin okuma modeliyle (CatalogView) çalışır; domain nesnesine erişen değiştirebilir de (P3-M3)");

    @ArchTest
    static final ArchRule nothing_depends_on_wiring = noClasses()
            .that().resideOutsideOfPackage("..storefront.config..")
            .should().dependOnClassesThat().resideInAPackage("..storefront.config..")
            .because("config her şeyi bilen tek yer (composition root); kimse ona bağımlı olmamalı");
}
