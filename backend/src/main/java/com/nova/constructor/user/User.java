package com.nova.constructor.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(length = 280)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 20)
    private ProfileVisibility profileVisibility = ProfileVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Theme theme = Theme.SYSTEM;

    @Column(nullable = false, length = 10)
    private String language = "pt-BR";

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at", nullable = false)
    private Instant termsAcceptedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "website_url", length = 200)
    private String websiteUrl;

    @Column(name = "github_username", length = 39)
    private String githubUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(String name, String username, String email, LocalDate birthDate, String passwordHash, Set<Role> roles,
                String termsVersion, Instant termsAcceptedAt) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.username = username;
        this.email = email;
        this.birthDate = birthDate;
        this.passwordHash = passwordHash;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.termsVersion = termsVersion;
        this.termsAcceptedAt = termsAcceptedAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Registra uma falha de login e bloqueia a conta ao atingir o limite. */
    public void registerFailedLogin(int maxAttempts, Instant lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = lockUntil;
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getGithubUsername() { return githubUsername; }

    /** Links do portfólio público. */
    public void updateLinks(String websiteUrl, String githubUsername) {
        this.websiteUrl = websiteUrl;
        this.githubUsername = githubUsername;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getBio() { return bio; }
    public ProfileVisibility getProfileVisibility() { return profileVisibility; }
    public Theme getTheme() { return theme; }
    public String getLanguage() { return language; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getPasswordHash() { return passwordHash; }
    public Set<Role> getRoles() { return roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles); }
    public boolean isEnabled() { return enabled; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getTermsVersion() { return termsVersion; }
    public Instant getTermsAcceptedAt() { return termsAcceptedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRoles(Set<Role> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Correção de dados pessoais (LGPD, art. 18, III). */
    public void updateAccount(String name, String username, String bio, LocalDate birthDate) {
        this.name = name;
        this.username = username;
        this.bio = bio;
        this.birthDate = birthDate;
    }

    /** Troca de e-mail: o novo endereço precisa ser verificado de novo. */
    public void changeEmail(String email) {
        this.email = email;
        this.emailVerified = false;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
    }
    public void setProfileVisibility(ProfileVisibility visibility) { this.profileVisibility = visibility; }
    public void setTheme(Theme theme) { this.theme = theme; }
    public void setLanguage(String language) { this.language = language; }
}
