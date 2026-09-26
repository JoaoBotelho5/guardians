package com.Guardians.newguardians.event;

import com.Guardians.newguardians.entity.Guard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.List;

/**
 * Quando o dono ataca alguma coisa (incluindo outro guard), qualquer guard seu no raio à volta
 * dele que ainda não tenha alvo próprio passa a atacar esse mesmo alvo — independentemente do
 * modo (Following, Staying ou Wandering). Um guard já a lutar com outra coisa não é redirecionado;
 * um guard em Staying/Wandering volta ao posto sozinho (ReturnToPostGoal) depois do combate.
 */
public class GuardCombatHandler {

    /** Alcance à volta do jogador onde os seus guards em Following "ouvem" o ataque dele. */
    private static final double ASSIST_RADIUS = 32.0D;

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity victim) || !victim.isAlive()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        List<Guard> assistingGuards = serverLevel.getEntitiesOfClass(Guard.class,
                player.getBoundingBox().inflate(ASSIST_RADIUS),
                guard -> guard.isAlive()
                        && guard != victim
                        && guard.isOwnedBy(player)
                        && guard.getTarget() == null);

        for (Guard guard : assistingGuards) {
            if (guard.canAttack(victim)) {
                guard.setTarget(victim);
            }
        }
    }
}