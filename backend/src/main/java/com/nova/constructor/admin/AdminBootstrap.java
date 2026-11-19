package com.nova.constructor.admin;

import com.nova.constructor.auth.AuthService;
import com.nova.constructor.config.NovaProperties;
import com.nova.constructor.user.Role;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cria o primeiro administrador a partir de NOVA_ADMIN_EMAIL/NOVA_ADMIN_PASSWORD, se ainda não existir. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final NovaProperties props;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(NovaProperties props, UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.props = props;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        NovaProperties.Admin admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank() || admin.password() == null || admin.password().isBlank()) {
            return;
        }
        String email = AuthService.normalizeEmail(admin.email());
        if (users.existsByEmail(email)) {
            return;
        }
        String username = "admin";
        for (int i = 2; users.existsByUsername(username); i++) {
            username = "admin" + i;
        }
        users.save(new User(admin.name(), username, email, LocalDate.of(1970, 1, 1), passwordEncoder.encode(admin.password()),
                EnumSet.of(Role.USER, Role.ADMIN), props.termsVersion(), clock.instant()));
        log.info("Administrador inicial criado: {}", email);
    }
}
