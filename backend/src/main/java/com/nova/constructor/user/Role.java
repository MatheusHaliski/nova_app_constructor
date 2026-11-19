package com.nova.constructor.user;

/** Perfis de acesso. No token viram a claim {@code roles} e, no Spring Security, {@code ROLE_<nome>}. */
public enum Role {
    USER,
    ADMIN
}
