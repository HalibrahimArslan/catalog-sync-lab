package com.rabbitlab.acceptance;

import com.rabbitlab.backoffice.BackofficeApplication;
import com.rabbitlab.storefront.StorefrontApplication;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Test edilen sistemin tamamı: bir PostgreSQL (iki veritabanı), bir RabbitMQ, iki uygulama.
 *
 * <pre>
 *   Cucumber adımları ──HTTP──▶ Backoffice ──outbox──▶ RabbitMQ ──▶ Storefront ◀──HTTP── Cucumber adımları
 *                                   │                                   │
 *                              backoffice DB                       storefront DB
 * </pre>
 *
 * İki uygulama aynı JVM'de ama ayrı Spring context'lerinde; birbirlerinin bean'lerini görmezler.
 * Konteynerler ve uygulamalar JVM başına bir kez açılır, senaryolar arasında paylaşılır (her senaryo
 * kendi SKU'sunu kullanır). Storefront, US5-1 için senaryo içinde kapatılıp açılabilir; Backoffice, US7 için
 * başka bir sözleşme sürümüyle yeniden başlatılabilir.
 */
final class SystemUnderTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management");

    private static ConfigurableApplicationContext backoffice;
    private static ConfigurableApplicationContext storefront;
    private static int schemaVersion = 1;
    private static RabbitTemplate rabbit;
    private static RabbitAdmin rabbitAdmin;

    private SystemUnderTest() {
    }

    static synchronized void start() {
        if (backoffice != null) {
            return;
        }
        POSTGRES.start();
        RABBIT.start();
        createDatabase("backoffice");
        createDatabase("storefront");

        backoffice = runBackoffice(schemaVersion);
        startStorefront();

        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        rabbit = new RabbitTemplate(connectionFactory);
        rabbitAdmin = new RabbitAdmin(connectionFactory);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopStorefront();
            backoffice.close();
            connectionFactory.destroy();
        }));
    }

    static synchronized void startStorefront() {
        if (storefront == null) {
            storefront = run(StorefrontApplication.class, "storefront", "--storefront.messaging.retry-delay=300ms");
        }
    }

    static synchronized void stopStorefront() {
        if (storefront != null) {
            storefront.close();
            storefront = null;
        }
    }

    /**
     * Backoffice'i verilen sözleşme sürümüyle yayınlayacak şekilde yeniden başlatır (gerçekte: ayarı değiştirip
     * yeniden deploy etmek). Outbox'ta bekleyen satırlar yazıldıkları sürümle gider.
     */
    static synchronized void publishSchemaVersion(int version) {
        if (version == schemaVersion) {
            return;
        }
        backoffice.close();
        backoffice = runBackoffice(version);
        schemaVersion = version;
    }

    /** Backoffice'in bu SKU için outbox'a yazdığı ve yayınladığı mesajların sürümleri, yazılış sırasıyla. */
    static List<Integer> publishedSchemaVersions(String sku) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("backoffice"), POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "select schema_version from outbox where sku = ? and published_at is not null order by id")) {
            statement.setString(1, sku);
            List<Integer> versions = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    versions.add(rows.getInt(1));
                }
            }
            return versions;
        } catch (SQLException e) {
            throw new IllegalStateException("Outbox okunamadı", e);
        }
    }

    static String backofficeUrl() {
        return "http://localhost:" + backoffice.getEnvironment().getProperty("local.server.port");
    }

    static String storefrontUrl() {
        return "http://localhost:" + storefront.getEnvironment().getProperty("local.server.port");
    }

    /** Backoffice'i atlayıp doğrudan RabbitMQ'ya mesaj göndermek ve park kuyruğuna bakmak için. */
    static RabbitTemplate rabbit() {
        return rabbit;
    }

    static RabbitAdmin rabbitAdmin() {
        return rabbitAdmin;
    }

    private static ConfigurableApplicationContext runBackoffice(int schemaVersion) {
        return run(BackofficeApplication.class, "backoffice", "--backoffice.outbox.poll-interval=100ms",
                "--backoffice.outbox.schema-version=" + schemaVersion);
    }

    /**
     * Uygulamayı gerçek main() ile aynı şekilde başlatır. Ayarlar komut satırı argümanı olarak verilir:
     * en yüksek öncelik onlarda, {@code backoffice.yml}'deki localhost ayarlarını ezerler.
     */
    private static ConfigurableApplicationContext run(Class<?> application, String name, String... extra) {
        String[] common = {
                "--spring.config.name=" + name,
                "--server.port=0",
                "--spring.datasource.url=" + jdbcUrl(name),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.rabbitmq.host=" + RABBIT.getHost(),
                "--spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                "--spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                "--spring.rabbitmq.password=" + RABBIT.getAdminPassword()};
        String[] args = new String[common.length + extra.length];
        System.arraycopy(common, 0, args, 0, common.length);
        System.arraycopy(extra, 0, args, common.length, extra.length);
        return new SpringApplicationBuilder(application).run(args);
    }

    private static String jdbcUrl(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
    }

    private static void createDatabase(String name) {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Veritabanı oluşturulamadı: " + name, e);
        }
    }
}
