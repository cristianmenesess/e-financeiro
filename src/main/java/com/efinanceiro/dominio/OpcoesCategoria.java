package com.efinanceiro.dominio;

import java.util.List;

/**
 * Ícones (nomes Lucide) e tons (do design system do front) que uma categoria pode usar. Fica só
 * aqui: o front busca a lista pela API pra montar os seletores, então os dois nunca divergem.
 */
public final class OpcoesCategoria {

    // Só os tons que têm classe ef-icon-tile--<tom> no design system (neutral = tile padrão)
    public static final List<String> TONS = List.of("brand", "positive", "negative", "warning", "ai", "neutral");

    public static final List<String> ICONES = List.of(
            "banknote", "receipt", "shopping-bag", "house", "ellipsis",
            "car", "fuel", "bus", "plane", "heart-pulse", "pill", "graduation-cap", "book-open",
            "gamepad-2", "film", "music", "shirt", "dog", "baby", "gift", "dumbbell", "utensils",
            "coffee", "smartphone", "wifi", "zap", "wrench", "briefcase", "laptop", "piggy-bank",
            "trending-up", "landmark", "hand-coins"
    );

    private OpcoesCategoria() {
    }
}
