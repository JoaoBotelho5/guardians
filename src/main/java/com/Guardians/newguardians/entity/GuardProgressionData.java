package com.Guardians.newguardians.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Dados de progressão do Guard: nível atual e XP acumulado dentro do nível atual.
 * Guardado como NeoForge data attachment, persiste ao gravar/carregar o chunk.
 */
public record GuardProgressionData(int level, int xp) {

    public static final GuardProgressionData DEFAULT = new GuardProgressionData(0, 0);

    /** Nível máximo que um Guard pode atingir; XP a mais é simplesmente ignorado depois disto. */
    public static final int MAX_LEVEL = 20;

    public static final Codec<GuardProgressionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(GuardProgressionData::level),
            Codec.INT.fieldOf("xp").forGetter(GuardProgressionData::xp)
    ).apply(instance, GuardProgressionData::new));

    /**
     * XP necessário para completar o próximo nível, em 4 patamares de 5 níveis: níveis 0-4 custam
     * 20 XP cada, 5-9 custam 30, 10-14 custam 40, 15-19 custam 50.
     */
    public static int xpRequiredForLevel(int level) {
        int stage = level / 5;
        return 20 + stage * 10;
    }

    public GuardProgressionData withAddedXp(int amount) {
        if (this.level >= MAX_LEVEL) {
            // Já no nível máximo: não acumula XP nenhum a mais.
            return this;
        }

        int newLevel = this.level;
        int newXp = this.xp + amount;

        int required = xpRequiredForLevel(newLevel);
        while (newLevel < MAX_LEVEL && newXp >= required) {
            newXp -= required;
            newLevel++;
            if (newLevel >= MAX_LEVEL) {
                break;
            }
            required = xpRequiredForLevel(newLevel);
        }

        if (newLevel >= MAX_LEVEL) {
            // Chegou ao topo: fixa em MAX_LEVEL com a barra de XP a zero, sem overflow guardado.
            return new GuardProgressionData(MAX_LEVEL, 0);
        }

        return new GuardProgressionData(newLevel, newXp);
    }
}