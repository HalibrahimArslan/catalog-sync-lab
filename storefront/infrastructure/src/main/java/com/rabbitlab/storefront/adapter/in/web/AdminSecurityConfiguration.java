package com.rabbitlab.storefront.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP güvenliği: {@code /admin/**} yalnızca yöneticiye (HTTP Basic), geri kalan her şey eskisi gibi
 * açık. Web adapter'ında, çünkü kimin hangi HTTP isteğini yapabileceği web'in sorusu; içteki halkalar
 * Spring Security'yi bilmez.
 *
 * <ul>
 *   <li>Oturum yok ({@code STATELESS}): her istek kimliğini kendisi taşır.</li>
 *   <li>CSRF kapalı: CSRF, tarayıcının çerezle otomatik gönderdiği oturumu kötüye kullanır; çerez ve
 *       oturum olmadığı için risk yok. Açık kalsaydı yeniden oynatma gibi POST'lar token isterdi.</li>
 *   <li>Şifre bellekte de hash'li (bcrypt). Bedeli: Basic'te her istek doğrulanır, her doğrulama birkaç
 *       on milisaniye.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
class AdminSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityConfiguration.class);

    @Bean
    SecurityFilterChain adminSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    UserDetailsService admins(AdminProperties admin, PasswordEncoder encoder) {
        if ("admin".equals(admin.password())) {
            log.warn("Yönetim uç noktaları varsayılan şifreyle korunuyor; ADMIN_PASSWORD ile değiştir");
        }
        return new InMemoryUserDetailsManager(User.withUsername(admin.username())
                .password(encoder.encode(admin.password()))
                .roles("ADMIN")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
