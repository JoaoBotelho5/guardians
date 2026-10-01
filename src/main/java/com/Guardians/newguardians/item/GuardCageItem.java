package com.Guardians.newguardians.item;

import com.Guardians.newguardians.client.data.PendingSoulsData;
import com.Guardians.newguardians.entity.Guard;
import com.Guardians.newguardians.entity.GuardMode;
import com.Guardians.newguardians.event.SoulCageRecovery;
import com.Guardians.newguardians.registry.ModEntities;
import com.Guardians.newguardians.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Item de duas fases, sempre a mesma classe: `filled = false` é a jaula vazia
 * (cage.png), `filled = true` é a jaula cheia / "soul cage" (soul_cage.png).

 * Vazia: clicar num Guard vivo apanha-o (guarda todo o NBT dele, incluindo
 * modo, posições guardadas, progressão e equipamento) e o item passa a cheio.
 * Cheia: clicar em cima de um bloco recria o Guard guardado nesse ponto, e o
 * item volta a vazio. Ciclo infinito entre as duas.
 */
public class GuardCageItem extends Item {

    private static final String GUARD_DATA_KEY = "GuardData";
    private static final String GUARD_NAME_KEY = "GuardName";
    private static final String GUARD_HEALTH_KEY = "GuardHealth";
    private static final String GUARD_MAX_HEALTH_KEY = "GuardMaxHealth";
    private static final String GUARD_LINK_KEY = "GuardLinkId";
    /** Tick de jogo (absoluto) em que o cooldown desta cage específica acaba. */
    private static final String GUARD_COOLDOWN_END_KEY = "GuardCooldownEnd";

    /** 10 minutos, igual ao cooldown de um escudo (mostrado com a mesma overlay). */
    private static final int SOUL_COOLDOWN_TICKS = 12000;

    private final boolean filled;

    public GuardCageItem(Properties properties, boolean filled) {
        super(properties);
        this.filled = filled;
    }

    // -------------------------------------------------- apanhar (vazia -> cheia)
    @SuppressWarnings("NullableProblems")
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (this.filled || !(target instanceof Guard guard) || player.level().isClientSide) {
            return InteractionResult.PASS;
        }

        if (!guard.isOwnedBy(player)) {
            // Só o dono do guard pode usar uma cage nele (um guard sem dono não é apanhável por ninguém).
            return InteractionResult.FAIL;
        }

        UUID existingLink = getLinkId(stack);
        if (existingLink != null && !existingLink.equals(guard.getSoulLinkedCageId())) {
            // Esta cage já está soul linked a outro guard: só pode apanhar de volta o guard dela.
            return InteractionResult.FAIL;
        }
        UUID linkId = existingLink != null ? existingLink : UUID.randomUUID();

        CompoundTag entityData = new CompoundTag();
        guard.saveWithoutId(entityData);

        CompoundTag customData = new CompoundTag();
        customData.put(GUARD_DATA_KEY, entityData);
        customData.putString(GUARD_NAME_KEY, guard.getDisplayName().getString());
        customData.putInt(GUARD_HEALTH_KEY, (int) Math.ceil(guard.getHealth()));
        customData.putInt(GUARD_MAX_HEALTH_KEY, (int) Math.ceil(guard.getMaxHealth()));
        customData.putUUID(GUARD_LINK_KEY, linkId);

        ItemStack filledStack = new ItemStack(ModItems.GUARD_CAGE_FILLED.get());
        filledStack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

        guard.discard();
        swap(player, hand, stack, filledStack);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------- reinvocar (cheia -> vazia)
    @SuppressWarnings("NullableProblems")
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!this.filled) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag rootTag = customData != null ? customData.copyTag() : null;
        CompoundTag entityData = rootTag != null ? rootTag.getCompound(GUARD_DATA_KEY) : null;
        if (entityData == null || entityData.isEmpty()) {
            return InteractionResult.FAIL;
        }

        if (rootTag.contains(GUARD_COOLDOWN_END_KEY)) {
            long remainingTicks = rootTag.getLong(GUARD_COOLDOWN_END_KEY) - serverLevel.getGameTime();
            if (remainingTicks > 0) {
                // Esta cage específica ainda está em cooldown (a alma acabou de voltar): não
                // reinvoca. Outras cages do mesmo jogador não são afetadas.
                return InteractionResult.FAIL;
            }
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());

        UUID linkId = getLinkId(stack);

        Guard guard = new Guard(ModEntities.GUARD.get(), serverLevel);
        guard.load(entityData);
        if (guard.getHealth() <= 0.0F) {
            // Salvaguarda: uma cage nunca deve reinvocar um guard já morto.
            guard.setHealth(guard.getMaxHealth());
        }
        guard.setSoulLinkedCageId(linkId);
        // Sair da cage entra sempre em Following (de quem a usou), independentemente do modo
        // que o guard tinha quando foi capturado.
        guard.setMode(GuardMode.FOLLOWING, context.getPlayer());
        guard.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, guard.getYRot(), guard.getXRot());
        guard.setDeltaMovement(Vec3.ZERO);
        guard.fallDistance = 0.0F;
        serverLevel.addFreshEntity(guard);
        // Só depois de estar no mundo (entity tracker ativo, sync normal para os clientes): reaplica
        // já aqui os efeitos permanentes do upgrade (ex. Resistência I), sem esperar pelo próximo tick.
        guard.maintainUpgradeEffects();

        if (context.getPlayer() != null) {
            // A cage vazia guarda o mesmo id e o nome do guard: é o que permite à alma voltar
            // exatamente para esta cage se o guard morrer no mundo, e mostrar quem está lá dentro.
            ItemStack emptyStack = new ItemStack(ModItems.GUARD_CAGE.get());
            if (linkId != null) {
                CompoundTag linkTag = new CompoundTag();
                linkTag.putUUID(GUARD_LINK_KEY, linkId);
                linkTag.putString(GUARD_NAME_KEY, guard.getDisplayName().getString());
                emptyStack.set(DataComponents.CUSTOM_DATA, CustomData.of(linkTag));
            }
            swap(context.getPlayer(), context.getHand(), stack, emptyStack);
        }
        return InteractionResult.SUCCESS;
    }

    /** Id "soul link" gravado na cage (cheia ou vazia), ou null se não estiver ligada a nenhum guard. */
    @Nullable
    private static UUID getLinkId(ItemStack cageStack) {
        CustomData customData = cageStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        return tag.hasUUID(GUARD_LINK_KEY) ? tag.getUUID(GUARD_LINK_KEY) : null;
    }

    /**
     * Chamado quando um guard "soul linked" morre. A alma (já com o cooldown de agora) fica
     * guardada no SavedData; a cage vazia ligada converte-se para cheia assim que o servidor
     * a "vir" (ver SoulCageRecovery). Nunca falha, por isso o guard nunca se perde.
     */
    public static boolean returnSoulToLinkedCage(ServerLevel level, Guard guard, UUID linkId) {
        MinecraftServer server = level.getServer();
        PendingSoulsData.get(server).put(linkId, buildSoulData(level, guard, linkId));
        // Caso comum: a cage está no inventário / menu aberto de alguém online -> converte já.
        SoulCageRecovery.scanOnlinePlayers(server);
        return true;
    }

    /**
     * Se `stack` for uma cage vazia ligada a uma alma pendente, consome a alma e devolve a cage
     * cheia que a substitui; caso contrário devolve null.
     */
    @Nullable
    public static ItemStack recoverSoul(MinecraftServer server, ItemStack stack) {
        if (!stack.is(ModItems.GUARD_CAGE.get())) {
            return null;
        }
        UUID linkId = getLinkId(stack);
        if (linkId == null) {
            return null;
        }
        CompoundTag soulData = PendingSoulsData.get(server).take(linkId);
        if (soulData == null) {
            return null;
        }
        ItemStack filledStack = new ItemStack(ModItems.GUARD_CAGE_FILLED.get());
        filledStack.set(DataComponents.CUSTOM_DATA, CustomData.of(soulData));
        return filledStack;
    }

    /** Dados da cage cheia no momento da morte: vida cheia, equipamento, e cooldown a começar agora. */
    private static CompoundTag buildSoulData(ServerLevel level, Guard guard, UUID linkId) {
        CompoundTag entityData = new CompoundTag();
        guard.saveWithoutId(entityData);
        int maxHealth = (int) Math.ceil(guard.getMaxHealth());
        entityData.putFloat("Health", maxHealth);

        CompoundTag customData = new CompoundTag();
        customData.put(GUARD_DATA_KEY, entityData);
        customData.putString(GUARD_NAME_KEY, guard.getDisplayName().getString());
        customData.putInt(GUARD_HEALTH_KEY, maxHealth);
        customData.putInt(GUARD_MAX_HEALTH_KEY, maxHealth);
        customData.putUUID(GUARD_LINK_KEY, linkId);
        customData.putLong(GUARD_COOLDOWN_END_KEY, level.getGameTime() + SOUL_COOLDOWN_TICKS);
        return customData;
    }

    /** Consome 1 do stack original e devolve o item resultante ao jogador. */
    private static void swap(Player player, InteractionHand hand, ItemStack original, ItemStack result) {
        if (original.getCount() <= 1) {
            // Substitui diretamente: nunca deixar o original a 0, senão o vanilla
            // (Player#interactOn) limpa a mão para EMPTY depois do SUCCESS, apagando o result.
            player.setItemInHand(hand, result);
        } else {
            original.shrink(1);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
    }

    /** Brilho de encantamento: a cage vazia só brilha quando está soul linked; a cheia brilha sempre. */
    @SuppressWarnings("NullableProblems")
    @Override
    public boolean isFoil(ItemStack stack) {
        return this.filled || getLinkId(stack) != null;
    }

    /** Nome do guard gravado na cage (cheia ou vazia-linked), ou "Guard" por omissão. */
    private static String getGuardName(ItemStack cageStack) {
        CustomData customData = cageStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return "Guard";
        }
        CompoundTag tag = customData.copyTag();
        return tag.contains(GUARD_NAME_KEY) ? tag.getString(GUARD_NAME_KEY) : "Guard";
    }

    // --------------------------------------------------------------- tooltip
    @SuppressWarnings("NullableProblems")
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (!this.filled) {
            if (getLinkId(stack) != null) {
                // Nome como linha literal própria (não depende de haver %s no lang) + o rótulo traduzido.
                lines.add(Component.literal(getGuardName(stack)).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("item.newguardians.guard_cage.soul_linked")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                lines.add(Component.translatable("item.newguardians.guard_cage.capture_hint")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            return;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        String name = getGuardName(stack);
        int health = tag.getInt(GUARD_HEALTH_KEY);
        int maxHealth = tag.getInt(GUARD_MAX_HEALTH_KEY);

        lines.add(Component.literal(name).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("HP: " + health + "/" + maxHealth).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.newguardians.guard_cage_filled.soul_linked")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        appendCooldownLine(lines, tag);
        lines.add(Component.translatable("item.newguardians.guard_cage_filled.spawn_hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Só corre no cliente (é onde a tooltip é desenhada): mostra o tempo que falta gravado
     * nesta cage específica, se ainda estiver em cooldown. Cada cage tem o seu próprio fim de
     * cooldown gravado no NBT, por isso isto nunca mostra (nem aplica) o cooldown de outra cage.
     */
    private void appendCooldownLine(List<Component> lines, CompoundTag tag) {
        if (FMLEnvironment.dist != Dist.CLIENT || !tag.contains(GUARD_COOLDOWN_END_KEY)) {
            return;
        }
        Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }
        long remainingTicks = tag.getLong(GUARD_COOLDOWN_END_KEY) - clientLevel.getGameTime();
        if (remainingTicks <= 0) {
            return;
        }
        int secondsLeft = Mth.ceil(remainingTicks / 20.0F);
        lines.add(Component.translatable("item.newguardians.guard_cage_filled.cooldown", secondsLeft)
                .withStyle(ChatFormatting.RED));
    }
}