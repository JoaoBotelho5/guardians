package com.Guardians.newguardians.entity;

import com.Guardians.newguardians.item.GuardCageItem;
import com.Guardians.newguardians.menu.GuardEquipmentMenu;
import com.Guardians.newguardians.registry.ModAttachments;
import com.Guardians.newguardians.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public class Guard extends PathfinderMob implements RangedAttackMob, CrossbowAttackMob {

    /** Sincronizado para o cliente animar a pose de "a puxar o crossbow" (igual ao Pillager). */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING_CROSSBOW =
            SynchedEntityData.defineId(Guard.class, EntityDataSerializers.BOOLEAN);

    private static final double MAX_HEALTH_PER_LEVEL = 2.0D;
    private static final double BASE_MAX_HEALTH = 30.0D;
    /** XP por ponto de vida máxima do inimigo morto: 4 HP de vida máxima = 1 XP (zombie 20 HP -> 5 XP). */
    private static final float XP_PER_MAX_HEALTH = 4.0F;

    // Following: mesmas distâncias do lobo (FollowOwnerGoal 10 / 2, teleporta a 12)
    private static final double FOLLOW_START_DISTANCE = 10.0D;
    private static final double FOLLOW_STOP_DISTANCE = 2.0D;
    private static final double FOLLOW_TELEPORT_DISTANCE = 12.0D;
    private static final double FOLLOW_FALLBACK_RANGE = 32.0D;

    // Staying: volta ao bloco guardado depois de combater
    private static final int STAY_RADIUS = 1;

    // Wandering: raio de 10 blocos à volta do ponto guardado
    private static final int WANDER_RADIUS = 10;

    // Aggro: monstros hostis passam a ter o guard como alvo, repartidos entre guards e jogadores
    private static final double AGGRO_RADIUS = 24.0D;
    private static final double AGGRO_RELEASE_RADIUS = 36.0D;
    private static final int AGGRO_INTERVAL = 10;

    // Bloqueio passivo: com um escudo na offhand, 50% de hipótese de bloquear o dano recebido
    private static final float SHIELD_BLOCK_CHANCE = 0.25F;

    // Regeneração passiva: mais rápida em combate, e +1 HP por regen a cada 5 níveis
    private static final int REGEN_INTERVAL_OUT_OF_COMBAT = 100; // 5s
    private static final int REGEN_INTERVAL_IN_COMBAT = 50;      // 2.5s
    private static final int REGEN_LEVELS_PER_BONUS = 5;

    // -------------------------------------------------------------- upgrades (item -> "UP")
    public static final int MAX_UPGRADE_LEVEL = 3;
    /** Item e quantidade necessários para cada nível (índice 0 = nível 1, etc.). Ajusta à vontade. */
    public static final Item[] UPGRADE_ITEMS = {Items.BOOK, Items.DIAMOND, Items.NETHERITE_SCRAP};
    public static final int[] UPGRADE_COSTS = {10, 5, 1};
    private static final double UPGRADE_1_BONUS_HP = 10.0D;
    private static final int UPGRADE_2_RESISTANCE_LEVEL = 2;
    private static final int UPGRADE_3_LEVEL = 3;
    private static final float UPGRADE_3_REGEN_BONUS = 3.0F;

    private static final String MODE_TAG = "GuardMode";
    private static final String STAY_POS_TAG = "GuardStayPos";
    private static final String WANDER_POS_TAG = "GuardWanderCenter";
    private static final String FOLLOW_TARGET_TAG = "GuardFollowTarget";
    private static final String SOUL_LINKED_TAG = "GuardSoulLinkId";
    private static final String OWNER_TAG = "GuardOwnerId";
    private static final String UPGRADE_LEVEL_TAG = "GuardUpgradeLevel";

    private GuardMode mode = GuardMode.FOLLOWING;
    @Nullable
    private BlockPos stayPos;
    @Nullable
    private BlockPos wanderCenter;
    @Nullable
    private UUID followTarget;
    /** Id da cage à qual este guard está "soul linked"; se não-nulo, a alma volta para essa cage exata ao morrer. */
    @Nullable
    private UUID soulLinkedCageId;
    /** Jogador dono deste guard (quem o pôs com o Guard Spawner, ou dono guardado na cage). */
    @Nullable
    private UUID ownerId;
    /** 0 = nenhum upgrade; 1-3 = nível de upgrade comprado com "UP" na GuardEquipmentScreen. */
    private int upgradeLevel = 0;
    /** Dano "congelado" no momento em que cada peça foi equipada; reforçado todo tick em maintainFrozenDurability(). */
    private final EnumMap<EquipmentSlot, Integer> frozenDamage = new EnumMap<>(EquipmentSlot.class);

    public Guard(EntityType<? extends Guard> type, Level level) {
        super(type, level);
        // 2.0F = always drops equipped items on death, undamaged
        for (EquipmentSlot slot : GuardEquipmentMenu.SLOTS) {
            this.setDropChance(slot, 2.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Os três são mutuamente exclusivos por causa do canUse()/canContinueToUse() de cada
        // um (verificam a arma equipada), por isso podem partilhar a mesma prioridade: nunca
        // há dois a quererem correr ao mesmo tempo.
        this.goalSelector.addGoal(1, new GuardMeleeAttackGoal());
        this.goalSelector.addGoal(1, new GuardBowAttackGoal());
        this.goalSelector.addGoal(1, new GuardCrossbowAttackGoal());
        this.goalSelector.addGoal(2, new FollowPlayerGoal());
        this.goalSelector.addGoal(3, new ReturnToPostGoal());
        this.goalSelector.addGoal(4, new WanderGoal(0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                m -> !(m instanceof Creeper)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGING_CROSSBOW, false);
    }

    // ------------------------------------------------------------ ranged (arco / crossbow)

    private boolean isHoldingBow() {
        return this.getMainHandItem().getItem() instanceof BowItem;
    }

    private boolean isHoldingCrossbow() {
        return this.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    private boolean isHoldingRangedWeapon() {
        return isHoldingBow() || isHoldingCrossbow();
    }

    /**
     * Chamado pelo RangedBowAttackGoal (arco) e, indiretamente, pelo RangedCrossbowAttackGoal
     * (crossbow, via performCrossbowAttack). Um único método serve as duas armas: com crossbow
     * delega no disparo vanilla (que já trata o "carregar/soltar"); com arco, dispara uma flecha
     * à mão, igual ao Skeleton, mas sem gerir munição própria (o guard nunca fica sem flechas).
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (isHoldingCrossbow()) {
            this.performCrossbowAttack(this, 1.6F);
            return;
        }
        shootArrowAt(target, distanceFactor);
    }

    /**
     * A seta disparada usa o efeito da que estiver na offhand (normal, tipped, spectral, etc.),
     * exatamente como um jogador — mas sem a consumir: o guard não gere munição própria, só o
     * "tipo" de seta vem da offhand. Se a offhand não tiver nenhuma seta lá (por ex. escudo),
     * dispara uma seta normal por omissão.
     */
    @Override
    public ItemStack getProjectile(ItemStack shootable) {
        ItemStack offhandStack = this.getOffhandItem();
        return offhandStack.getItem() instanceof ArrowItem ? offhandStack : new ItemStack(Items.ARROW);
    }

    private void shootArrowAt(LivingEntity target, float velocity) {
        ItemStack weapon = this.getMainHandItem();
        ItemStack ammo = getProjectile(weapon);
        ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(this.level(), ammo, this, weapon);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontalDist * 0.20000000298023224D, dz, 1.6F,
                (float) (14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    @Override
    public void setChargingCrossbow(boolean chargingCrossbow) {
        this.entityData.set(DATA_CHARGING_CROSSBOW, chargingCrossbow);
    }

    /** Não faz parte da interface CrossbowAttackMob (só o setter faz) — é só um getter de
     *  conveniência, para um futuro renderer poder mostrar a pose de "a carregar o crossbow". */
    public boolean isChargingCrossbow() {
        return this.entityData.get(DATA_CHARGING_CROSSBOW);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    /** Só ativo enquanto o guard tiver mesmo um arco equipado; senão cede lugar ao melee/crossbow. */
    private class GuardBowAttackGoal extends RangedBowAttackGoal<Guard> {
        GuardBowAttackGoal() {
            super(Guard.this, 1.0D, 20, 15.0F);
        }

        @Override
        public boolean canUse() {
            return isHoldingBow() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isHoldingBow() && super.canContinueToUse();
        }
    }

    /** Só ativo enquanto o guard tiver mesmo um crossbow equipado; senão cede lugar ao melee/arco. */
    private class GuardCrossbowAttackGoal extends RangedCrossbowAttackGoal<Guard> {
        GuardCrossbowAttackGoal() {
            super(Guard.this, 1.0D, 15.0F);
        }

        @Override
        public boolean canUse() {
            return isHoldingCrossbow() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isHoldingCrossbow() && super.canContinueToUse();
        }
    }

    /** Cede lugar ao arco/crossbow assim que o guard tiver uma dessas armas equipada. */
    private class GuardMeleeAttackGoal extends MeleeAttackGoal {
        GuardMeleeAttackGoal() {
            super(Guard.this, 1.2D, true);
        }

        @Override
        public boolean canUse() {
            return !isHoldingRangedWeapon() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !isHoldingRangedWeapon() && super.canContinueToUse();
        }
    }

    /**
     * O guard ignora por completo qualquer dano do seu próprio dono, e qualquer dano de outro guard
     * do mesmo dono (fogo amigo, incluindo projéteis: source.getEntity() devolve sempre quem disparou,
     * não a seta). Vindo de outra fonte, defende-se: fica com o atacante como alvo mesmo que bloqueie
     * o dano com o escudo.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (this.ownerId != null && attacker != null) {
            if (this.ownerId.equals(attacker.getUUID())) {
                return false;
            }
            if (attacker instanceof Guard otherGuard && this.ownerId.equals(otherGuard.ownerId)) {
                return false;
            }
        }
        if (attacker instanceof LivingEntity livingAttacker && !(livingAttacker instanceof Creeper) && !hasLiveTarget()) {
            this.setTarget(livingAttacker);
        }
        if (canBlockWithOffhandShield(source) && this.random.nextFloat() < SHIELD_BLOCK_CHANCE) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
            return false;
        }
        return super.hurt(source, amount);
    }

    /** true se o alvo atual existir e ainda estiver vivo (um alvo morto não conta como "ocupado"). */
    private boolean hasLiveTarget() {
        LivingEntity target = this.getTarget();
        return target != null && target.isAlive();
    }

    private boolean canBlockWithOffhandShield(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false; // lava (e outro fogo) sempre passa
        }
        if (!(this.getItemBySlot(EquipmentSlot.OFFHAND).getItem() instanceof ShieldItem)) {
            return false; // só bloqueia com escudo na offhand
        }
        return true;
    }

    /** O guard não sofre dano de sufocamento (ficar preso dentro de um bloco). */
    @Override
    public boolean isInWall() {
        return false;
    }

    /**
     * Se o guard estiver "soul linked", a alma (com equipamento) fica guardada no SavedData e a
     * cage vazia ligada converte-se para cheia assim que for vista pelo servidor (ver
     * SoulCageRecovery). O equipamento não cai no chão: vai na alma.
     */
    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide
                && !this.isRemoved() && !this.dead   // evita gravar duas vezes (a segunda já sem equipamento)
                && this.level() instanceof ServerLevel serverLevel
                && this.soulLinkedCageId != null) {
            GuardCageItem.returnSoulToLinkedCage(serverLevel, this, this.soulLinkedCageId);
            for (EquipmentSlot slot : GuardEquipmentMenu.SLOTS) {
                this.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        super.die(damageSource);
    }

    // ------------------------------------------------------------------ modes

    public GuardMode getMode() {
        return this.mode;
    }

    /** Muda o modo e guarda as coordenadas relevantes (posição atual do guard). */
    public void setMode(GuardMode newMode, @Nullable Player player) {
        this.mode = newMode;
        switch (newMode) {
            case FOLLOWING -> {
                if (player != null) {
                    this.followTarget = player.getUUID();
                }
            }
            case STAYING -> this.stayPos = this.blockPosition();
            case WANDERING -> this.wanderCenter = this.blockPosition();
        }
        applyMode();
    }

    @Nullable
    public UUID getSoulLinkedCageId() {
        return this.soulLinkedCageId;
    }

    public void setSoulLinkedCageId(@Nullable UUID soulLinkedCageId) {
        this.soulLinkedCageId = soulLinkedCageId;
    }

    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(@Nullable UUID ownerId) {
        this.ownerId = ownerId;
    }

    /** true só se este guard tiver dono e for exatamente este jogador. Sem dono, ninguém pode usá-lo. */
    public boolean isOwnedBy(Player player) {
        return this.ownerId != null && this.ownerId.equals(player.getUUID());
    }

    /**
     * Qualquer item equipado (armadura, mainhand, offhand) fica com a durabilidade que tinha no
     * momento do equip: não fica reparado, mas também nunca se gasta mais nem parte, seja por
     * combate, disparo, etc. — maintainFrozenDurability() (chamado todo tick) repõe o valor.
     * Cobre tanto o equipamento inicial como qualquer troca feita na GuardEquipmentScreen (que passa
     * sempre por aqui via GuardEquipmentContainer.setItem).
     */
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            this.frozenDamage.put(slot, stack.getDamageValue());
        } else {
            this.frozenDamage.remove(slot);
        }
    }

    /** Reforça todo tick (server-side) o valor de dano congelado de cada peça equipada, anulando
     *  qualquer hurtAndBreak que o combate/disparo tenha aplicado entretanto. */
    private void maintainFrozenDurability() {
        for (EquipmentSlot slot : GuardEquipmentMenu.SLOTS) {
            Integer frozen = this.frozenDamage.get(slot);
            if (frozen == null) {
                continue;
            }
            ItemStack stack = this.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                this.frozenDamage.remove(slot);
                continue;
            }
            if (stack.getDamageValue() != frozen) {
                stack.setDamageValue(frozen);
            }
        }
    }

    /** Guards do mesmo dono nunca se atacam entre si, seja qual for a forma como o alvo foi definido
     *  (ataque direto, assist do GuardCombatHandler, etc.). */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof Guard otherGuard && this.ownerId != null && this.ownerId.equals(otherGuard.ownerId)) {
            return false;
        }
        return super.canAttack(target);
    }

    /**
     * NÃO usar Mob.restrictTo: o MeleeAttackGoal vanilla (e o TargetChasingGoal do Epic Fight,
     * que estende MeleeAttackGoal) pára de perseguir quando o alvo sai da restrição, o que
     * fazia o guard andar 1 passo e parar em Staying. O ponto é controlado só por este código.
     */
    private void applyMode() {
        switch (this.mode) {
            case STAYING -> {
                if (this.stayPos == null) {
                    this.stayPos = this.blockPosition();
                }
            }
            case WANDERING -> {
                if (this.wanderCenter == null) {
                    this.wanderCenter = this.blockPosition();
                }
            }
            case FOLLOWING -> {
            }
        }
        this.clearRestriction();
        this.getNavigation().stop();
    }

    private boolean isWithinPost() {
        BlockPos center = getPostCenter();
        if (center == null) {
            return true;
        }
        double radius = this.mode == GuardMode.STAYING ? STAY_RADIUS : WANDER_RADIUS;
        return this.blockPosition().distSqr(center) < radius * radius;
    }

    @Nullable
    private BlockPos getPostCenter() {
        return switch (this.mode) {
            case STAYING -> this.stayPos;
            case WANDERING -> this.wanderCenter;
            case FOLLOWING -> null;
        };
    }

    private void cycleMode(ServerPlayer player) {
        setMode(this.mode.next(), player);
        BlockPos center = getPostCenter();
        String where = center == null ? "" : " (" + center.getX() + ", " + center.getY() + ", " + center.getZ() + ")";
        player.displayClientMessage(Component.literal("Guard: " + this.mode.getDisplayName() + where), true);
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).is(ModItems.GUARD_CAGE.get())) {
            // Deixa a GuardCageItem.interactLivingEntity tratar a captura.
            return InteractionResult.PASS;
        }
        if (!isOwnedBy(player)) {
            if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.literal("This guard doesn't belong to you"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                cycleMode(serverPlayer);
            } else {
                serverPlayer.openMenu(new SimpleMenuProvider(
                                (id, inv, p) -> new GuardEquipmentMenu(id, inv, this),
                                Component.literal("Guard")),
                        buf -> buf.writeVarInt(this.getId()));
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(MODE_TAG, this.mode.name());
        writePos(tag, STAY_POS_TAG, this.stayPos);
        writePos(tag, WANDER_POS_TAG, this.wanderCenter);
        if (this.followTarget != null) {
            tag.putUUID(FOLLOW_TARGET_TAG, this.followTarget);
        }
        if (this.soulLinkedCageId != null) {
            tag.putUUID(SOUL_LINKED_TAG, this.soulLinkedCageId);
        }
        if (this.ownerId != null) {
            tag.putUUID(OWNER_TAG, this.ownerId);
        }
        tag.putInt(UPGRADE_LEVEL_TAG, this.upgradeLevel);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.mode = tag.contains(MODE_TAG, Tag.TAG_STRING)
                ? GuardMode.byName(tag.getString(MODE_TAG))
                : GuardMode.FOLLOWING;
        this.stayPos = readPos(tag, STAY_POS_TAG);
        this.wanderCenter = readPos(tag, WANDER_POS_TAG);
        this.followTarget = tag.hasUUID(FOLLOW_TARGET_TAG) ? tag.getUUID(FOLLOW_TARGET_TAG) : null;
        this.soulLinkedCageId = tag.hasUUID(SOUL_LINKED_TAG) ? tag.getUUID(SOUL_LINKED_TAG) : null;
        this.ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        this.upgradeLevel = tag.contains(UPGRADE_LEVEL_TAG, Tag.TAG_INT) ? tag.getInt(UPGRADE_LEVEL_TAG) : 0;
        // a restrição do Mob não é guardada pelo vanilla, reaplica
        applyMode();
        // O equipamento é lido direto do NBT (não passa por setItemSlot), por isso o dano
        // congelado tem de ser reposto aqui: senão, depois de reinvocar da cage ou recarregar
        // o chunk, nada ficava congelado até alguém re-equipar a peça.
        this.frozenDamage.clear();
        for (EquipmentSlot slot : GuardEquipmentMenu.SLOTS) {
            ItemStack equipped = this.getItemBySlot(slot);
            if (!equipped.isEmpty() && equipped.isDamageableItem()) {
                this.frozenDamage.put(slot, equipped.getDamageValue());
            }
        }
    }

    private static void writePos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        }
    }

    @Nullable
    private static BlockPos readPos(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT_ARRAY)) {
            return null;
        }
        int[] a = tag.getIntArray(key);
        return a.length == 3 ? new BlockPos(a[0], a[1], a[2]) : null;
    }

    // ------------------------------------------------------------------- misc

    /** Guard nunca faz despawn por distância (importante em Staying/Wandering). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            this.updateSwingTime();
        } else {
            clearDeadTarget();
            teleportToFollowedPlayerIfTooFar();
            attractHostileMobs();
            regenerateHealth();
            maintainUpgradeEffects();
            maintainFrozenDurability();
        }
    }

    /**
     * Reaplica a Resistência I permanente do upgrade nível 2 sempre que faltar ou esteja errada —
     * em vez de confiar só na gravação normal do efeito (ActiveEffects), garante que sobrevive a
     * qualquer coisa que possa tirar/alterar o efeito ao guard (poção de cura, comando, dados
     * antigos vindos de uma cage gravada antes desta fix, etc.). Compara a duração em vez de só
     * hasEffect(): um efeito já presente mas com duração errada (ex. antiga, quase a expirar) não
     * passa despercebido — é substituído. Público porque a GuardCageItem chama isto explicitamente
     * logo a seguir a reinvocar o guard, sem esperar pelo próximo tick.
     */
    public void maintainUpgradeEffects() {
        if (this.upgradeLevel < UPGRADE_2_RESISTANCE_LEVEL) {
            return;
        }
        MobEffectInstance current = this.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (current == null || current.getDuration() < Integer.MAX_VALUE - 200) {
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, true, false, false));
        }
    }

    /**
     * Sem isto, um alvo definido à mão (autodefesa em hurt(), ou o assist do dono a atacar algo)
     * ficava "preso" para sempre depois de morrer: ao contrário do NearestAttackableTargetGoal
     * (que só serve para monstros hostis), não há nenhum Goal a limpar o alvo deste guard quando
     * ele morre, então o guard nunca mais se defendia nem voltava ao posto (Staying/Wandering).
     */
    private void clearDeadTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) {
            this.setTarget(null);
        }
    }

    /** 1 HP a cada 5s parado, 1 HP a cada 2.5s com um alvo ativo; +1 HP por regen a cada 5 níveis. */
    private void regenerateHealth() {
        if (!this.isAlive() || this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        int interval = this.getTarget() != null ? REGEN_INTERVAL_IN_COMBAT : REGEN_INTERVAL_OUT_OF_COMBAT;
        if (this.tickCount % interval == 0) {
            this.heal(getRegenAmount());
        }
    }

    /** +1 HP por regen a cada 5 níveis (nível 0-4: 1hp, 5-9: 2hp, 10-14: 3hp, ...), +3 HP fixo com o upgrade nível 3. */
    private float getRegenAmount() {
        float amount = 1.0F + (getGuardLevel() / REGEN_LEVELS_PER_BONUS);
        if (this.upgradeLevel >= UPGRADE_3_LEVEL) {
            amount += UPGRADE_3_REGEN_BONUS;
        }
        return amount;
    }

    /**
     * Faz monstros hostis terem este guard como alvo, com o número de atacantes por guard limitado.
     * Cada monstro conta como 1; os alvos possíveis são os guards e os jogadores num raio de 24 blocos.
     * Com jogadores por perto: máx. monstros / (guards + jogadores) por guard (mín. 1, arredondado
     * para baixo). Sem jogadores: monstros / guards, arredondado para cima.
     * Só "rouba" monstros sem alvo ou com um jogador como alvo que esteja mais longe do que o guard.
     */
    private void attractHostileMobs() {
        if ((this.tickCount + this.getId()) % AGGRO_INTERVAL != 0) {
            return;
        }
        double radiusSqr = AGGRO_RADIUS * AGGRO_RADIUS;
        double releaseSqr = AGGRO_RELEASE_RADIUS * AGGRO_RELEASE_RADIUS;

        List<Monster> nearby = this.level().getEntitiesOfClass(Monster.class,
                this.getBoundingBox().inflate(AGGRO_RELEASE_RADIUS + 8.0D),
                m -> m.isAlive() && isEligibleAggressor(m));

        List<Monster> inRange = new ArrayList<>();
        int attackers = 0;
        for (Monster m : nearby) {
            double distSqr = m.distanceToSqr(this);
            if (m.getTarget() == this) {
                // sem TargetGoal a validar, um alvo posto à mão nunca é largado: solta os que ficaram longe
                if (distSqr > releaseSqr) {
                    m.setTarget(null);
                } else {
                    attackers++;
                }
            }
            if (distSqr <= radiusSqr) {
                inRange.add(m);
            }
        }
        if (inRange.isEmpty()) {
            return;
        }

        AABB box = this.getBoundingBox().inflate(AGGRO_RADIUS);
        int guards = this.level().getEntitiesOfClass(Guard.class, box, Guard::isAlive).size();
        int players = this.level().getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).size();

        int cap = players == 0
                ? Mth.ceil(inRange.size() / (float) guards)
                : Math.max(1, inRange.size() / (guards + players));
        if (attackers >= cap) {
            return;
        }

        List<Monster> candidates = new ArrayList<>();
        for (Monster m : inRange) {
            if (canRecruit(m)) {
                candidates.add(m);
            }
        }
        candidates.sort(Comparator.comparingDouble(m -> m.distanceToSqr(this)));

        for (Monster m : candidates) {
            if (attackers >= cap) {
                break;
            }
            provoke(m);
            attackers++;
        }
    }

    /** Neutros só atacam se provocados — exceto enderman e zombified piglin. Creeper nunca entra
     *  nesta guerra (nem o guard o provoca, nem ele conta como agressor). */
    private static boolean isEligibleAggressor(Monster m) {
        if (m instanceof Creeper) {
            return false;
        }
        return !(m instanceof NeutralMob) || m instanceof EnderMan || m instanceof ZombifiedPiglin;
    }

    /** Define o alvo e, para NeutralMob, a anger persistente (senão o goal deles reverte o alvo). */
    private void provoke(Monster m) {
        m.setTarget(this);
        if (m instanceof NeutralMob neutral) {
            neutral.setPersistentAngerTarget(this.getUUID());
            neutral.startPersistentAngerTimer();
        }
    }

    private boolean canRecruit(Monster m) {
        LivingEntity current = m.getTarget();
        if (current == this) {
            return false;
        }
        if (current != null && current.isAlive()) {
            if (!(current instanceof Player)) {
                return false; // já está a atacar outro guard ou outra coisa
            }
            boolean revenge = m.getLastHurtByMob() == current && m.tickCount - m.getLastHurtByMobTimestamp() < 100;
            if (revenge || m.distanceToSqr(current) <= m.distanceToSqr(this)) {
                return false; // o jogador é mais próximo ou acabou de o atingir
            }
        }
        return m.canAttack(this) && m.getSensing().hasLineOfSight(this);
    }

    @Nullable
    private Player resolveFollowPlayer() {
        Player p = this.followTarget != null
                ? this.level().getPlayerByUUID(this.followTarget)
                : this.level().getNearestPlayer(this, FOLLOW_FALLBACK_RANGE);
        return p != null && p.isAlive() && !p.isSpectator() ? p : null;
    }

    /** Corre em aiStep, fora dos goals: o teleporte tem prioridade sobre atacar. */
    private void teleportToFollowedPlayerIfTooFar() {
        if (this.mode != GuardMode.FOLLOWING || this.tickCount % 10 != 0) {
            return;
        }
        Player player = resolveFollowPlayer();
        if (player != null
                && this.distanceToSqr(player) >= FOLLOW_TELEPORT_DISTANCE * FOLLOW_TELEPORT_DISTANCE) {
            tryTeleportNear(player);
        }
    }

    /**
     * O guard nunca absorve XP orbs largados no mundo (nem os de mobs que ele próprio mate,
     * nem os de kills de jogadores por perto) — o único XP que ganha vem diretamente daqui.
     * O XP dado por cada mob hostil (Monster) morto escala com a vida máxima da vítima: 4 HP
     * de vida máxima = 1 XP (zombie com 20 HP -> 5 XP; zombie com 40 HP -> 10 XP), mesmo com
     * um jogador por perto ou a participar no combate.
     */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        boolean result = super.killedEntity(level, victim);
        if (victim instanceof Monster) {
            addExperience(xpForKill(victim));
        }
        return result;
    }

    /** XP dado por matar esta vítima: vida máxima / 4, arredondado, com mínimo de 1. */
    private static int xpForKill(LivingEntity victim) {
        return Math.max(1, Math.round(victim.getMaxHealth() / XP_PER_MAX_HEALTH));
    }

    public void addExperience(int amount) {
        if (amount <= 0 || this.level().isClientSide) {
            return;
        }

        GuardProgressionData current = this.getData(ModAttachments.GUARD_PROGRESSION);
        int previousLevel = current.level();

        GuardProgressionData updated = current.withAddedXp(amount);
        this.setData(ModAttachments.GUARD_PROGRESSION, updated);

        if (updated.level() > previousLevel) {
            onLevelUp(updated.level());
        }
    }

    private void onLevelUp(int newLevel) {
        var attribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            double newMax = computeMaxHealth(newLevel);
            attribute.setBaseValue(newMax);
            this.setHealth((float) newMax);
        }
    }

    /** Vida máxima base pelo nível de XP, mais o bónus fixo do upgrade nível 1 se já comprado. */
    private double computeMaxHealth(int guardLevel) {
        double max = BASE_MAX_HEALTH + (MAX_HEALTH_PER_LEVEL * guardLevel);
        if (this.upgradeLevel >= 1) {
            max += UPGRADE_1_BONUS_HP;
        }
        return max;
    }

    public int getGuardLevel() {
        return this.getData(ModAttachments.GUARD_PROGRESSION).level();
    }

    /** XP acumulado dentro do nível atual. */
    public int getGuardXp() {
        return this.getData(ModAttachments.GUARD_PROGRESSION).xp();
    }

    // ------------------------------------------------------------------ upgrades

    public int getUpgradeLevel() {
        return this.upgradeLevel;
    }

    /**
     * Chamado pelo GuardEquipmentMenu quando se carrega em "UP" (só do lado servidor). Verifica
     * se há um próximo nível, se o jogador tem os itens todos, consome-os do inventário dele e
     * só depois aplica o upgrade. Devolve false (sem consumir nada) se faltar algum requisito.
     */
    public boolean tryUpgrade(Inventory playerInventory) {
        int nextLevel = this.upgradeLevel + 1;
        if (nextLevel > MAX_UPGRADE_LEVEL) {
            return false;
        }
        Item requiredItem = UPGRADE_ITEMS[nextLevel - 1];
        int requiredCount = UPGRADE_COSTS[nextLevel - 1];
        if (!consumeItems(playerInventory, requiredItem, requiredCount)) {
            return false;
        }
        applyUpgrade(nextLevel);
        return true;
    }

    /** Só consome se houver o suficiente — nunca tira alguns itens e falha a meio. */
    private static boolean consumeItems(Inventory inventory, Item item, int count) {
        int available = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                available += inventory.getItem(i).getCount();
            }
        }
        if (available < count) {
            return false;
        }
        int remaining = count;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return true;
    }

    /**
     * Nível 1: +10 vida máxima, aplicado já aqui (e dobrado na fórmula de computeMaxHealth para
     * sobreviver a subidas de nível futuras). Níveis 2 e 3 (resistência permanente / bónus de
     * regen) não precisam de nada aqui: já são aplicados de forma contínua a partir do próprio
     * upgradeLevel (ver maintainUpgradeEffects() e getRegenAmount()), e o upgradeLevel em si já
     * é persistido em readAdditionalSaveData/addAdditionalSaveData — sobrevive à cage sozinho.
     */
    private void applyUpgrade(int newLevel) {
        this.upgradeLevel = newLevel;
        if (newLevel == 1) {
            var attribute = this.getAttribute(Attributes.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(computeMaxHealth(getGuardLevel()));
            }
            this.heal((float) UPGRADE_1_BONUS_HP);
        }
    }

    // ------------------------------------------------------------------ goals

    /** Segue o jogador como um lobo: começa a 10 blocos, pára a 2 (o teleporte a 12 está em aiStep). */
    private class FollowPlayerGoal extends Goal {
        @Nullable
        private Player target;
        private int recalcDelay;

        FollowPlayerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mode != GuardMode.FOLLOWING) {
                return false;
            }
            Player p = resolveFollowPlayer();
            if (p == null || Guard.this.distanceToSqr(p) < FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE) {
                return false;
            }
            this.target = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return mode == GuardMode.FOLLOWING
                    && this.target != null
                    && this.target.isAlive()
                    && !this.target.isSpectator()
                    && !Guard.this.getNavigation().isDone()
                    && Guard.this.distanceToSqr(this.target) > FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE;
        }

        @Override
        public void start() {
            this.recalcDelay = 0;
        }

        @Override
        public void stop() {
            this.target = null;
            Guard.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            Guard.this.getLookControl().setLookAt(this.target, 10.0F, (float) Guard.this.getMaxHeadXRot());
            if (--this.recalcDelay <= 0) {
                this.recalcDelay = this.adjustedTickDelay(10);
                Guard.this.getNavigation().moveTo(this.target, 1.0D);
            }
        }
    }

    /** Staying: volta ao bloco guardado. Wandering: volta para dentro do raio. Só sem alvo. */
    private class ReturnToPostGoal extends Goal {

        ReturnToPostGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mode != GuardMode.FOLLOWING
                    && Guard.this.getTarget() == null
                    && !Guard.this.isWithinPost()
                    && Guard.this.getRandom().nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return mode != GuardMode.FOLLOWING
                    && Guard.this.getTarget() == null
                    && !Guard.this.getNavigation().isDone()
                    && (mode == GuardMode.STAYING || !Guard.this.isWithinPost());
        }

        @Override
        public void start() {
            BlockPos c = Guard.this.getPostCenter();
            if (c != null) {
                Guard.this.getNavigation().moveTo(c.getX() + 0.5D, c.getY(), c.getZ() + 0.5D, 1.0D);
            }
        }

        @Override
        public void stop() {
            Guard.this.getNavigation().stop();
        }
    }

    /** Passeio aleatório dentro do raio de 10 blocos do ponto guardado. Só em Wandering. */
    private class WanderGoal extends Goal {
        private final double speed;
        private double wantedX;
        private double wantedY;
        private double wantedZ;

        WanderGoal(double speed) {
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mode != GuardMode.WANDERING || wanderCenter == null || Guard.this.getTarget() != null
                    || Guard.this.getRandom().nextInt(this.reducedTickDelay(120)) != 0) {
                return false;
            }
            BlockPos spot = pickSpot();
            if (spot == null) {
                return false;
            }
            this.wantedX = spot.getX() + 0.5D;
            this.wantedY = spot.getY();
            this.wantedZ = spot.getZ() + 0.5D;
            return true;
        }

        @Nullable
        private BlockPos pickSpot() {
            BlockPos center = wanderCenter;
            for (int i = 0; i < 10; i++) {
                double angle = Guard.this.getRandom().nextDouble() * Math.PI * 2.0D;
                double dist = Math.sqrt(Guard.this.getRandom().nextDouble()) * (WANDER_RADIUS - 1);
                int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
                for (int dy = 3; dy >= -4; dy--) {
                    BlockPos p = new BlockPos(x, center.getY() + dy, z);
                    if (Guard.this.isStandable(p)) {
                        return p;
                    }
                }
            }
            return null;
        }

        @Override
        public boolean canContinueToUse() {
            return mode == GuardMode.WANDERING
                    && Guard.this.getTarget() == null
                    && !Guard.this.getNavigation().isDone();
        }

        @Override
        public void start() {
            Guard.this.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speed);
        }

        @Override
        public void stop() {
            Guard.this.getNavigation().stop();
        }
    }

    // --------------------------------------------------------------- teleport

    /** Teleporta para junto do jogador, sem fall damage. */
    private boolean tryTeleportNear(Player player) {
        BlockPos base = player.blockPosition();
        for (int i = 0; i < 10; i++) {
            int x = base.getX() + this.random.nextIntBetweenInclusive(-3, 3);
            int y = base.getY() + this.random.nextIntBetweenInclusive(-1, 1);
            int z = base.getZ() + this.random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(x - player.getX()) < 2.0D && Math.abs(z - player.getZ()) < 2.0D) {
                continue;
            }
            if (isStandable(new BlockPos(x, y, z))) {
                this.moveTo(x + 0.5D, y, z + 0.5D, this.getYRot(), this.getXRot());
                this.getNavigation().stop();
                this.fallDistance = 0.0F;
                this.setDeltaMovement(Vec3.ZERO);
                return true;
            }
        }
        return false;
    }

    /** Chão sólido por baixo, sem líquido e com espaço livre para o guard. */
    private boolean isStandable(BlockPos pos) {
        BlockPos below = pos.below();
        if (!this.level().getBlockState(below).isFaceSturdy(this.level(), below, Direction.UP)) {
            return false;
        }
        if (!this.level().getFluidState(pos).isEmpty()) {
            return false;
        }
        return this.level().noCollision(this, this.getBoundingBox().move(
                pos.getX() + 0.5D - this.getX(),
                pos.getY() - this.getY(),
                pos.getZ() + 0.5D - this.getZ()));
    }
}