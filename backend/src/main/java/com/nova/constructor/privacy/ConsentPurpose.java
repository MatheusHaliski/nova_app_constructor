package com.nova.constructor.privacy;

/**
 * Finalidades que dependem de consentimento (LGPD, art. 7º, I). Uma por finalidade, nunca agrupadas; todas nascem
 * desligadas (privacy by default) e podem ser revogadas a qualquer momento (art. 8º, §5º e art. 18, IX).
 */
public enum ConsentPurpose {
    /** Recomendações do feed a partir do histórico de uso. */
    PERSONALIZED_FEED,
    /** Métricas de uso para melhorar o produto (cookies não essenciais). */
    PRODUCT_ANALYTICS,
    /** E-mails de novidades e conteúdo. */
    MARKETING_EMAILS,
    /** Uso dos meus projetos e conversas para melhorar a IA do NOVA. */
    AI_IMPROVEMENT,
    /** Compartilhamento de dados com parceiros. */
    PARTNER_SHARING
}
