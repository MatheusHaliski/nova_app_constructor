package com.nova.constructor.user;

/** Quem pode ver o perfil (LGPD, art. 18, VII). O padrão é o mais protetivo: PRIVATE. */
public enum ProfileVisibility {
    /** Somente você. */
    PRIVATE,
    /** Usuários logados da rede NOVA. */
    COMMUNITY,
    /** Qualquer pessoa, inclusive buscadores. */
    PUBLIC
}
