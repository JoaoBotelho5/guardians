package com.Guardians.newguardians.menu;

import com.Guardians.newguardians.entity.Guard;
import com.Guardians.newguardians.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class GuardEquipmentMenu extends AbstractContainerMenu {

    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private static final int[][] POS = {
            {8, 8}, {8, 26}, {8, 44}, {8, 62}, {77, 62}, {77, 44}
    };

    private static final int EQUIP_END = 6;
    private static final int MAIN_END = 33;
    private static final int HOTBAR_END = 42;

    // Stats sincronizados server -> client (ContainerData é enviado como short)
    private static final int STAT_HEALTH = 0;
    private static final int STAT_MAX_HEALTH = 1;
    private static final int STAT_ARMOR = 2;
    private static final int STAT_DAMAGE_X10 = 3;
    private static final int STAT_LEVEL = 4;
    private static final int STAT_XP = 5;
    private static final int STAT_UPGRADE_LEVEL = 6;
    private static final int STAT_COUNT = 7;

    /** Id do botão "UP" enviado por ServerboundContainerButtonClickPacket (ver clickMenuButton). */
    public static final int BUTTON_UPGRADE = 0;

    private final Container equipment;
    @Nullable
    private final Guard guard;
    private final ContainerData stats;
    /** Guardado para o "UP" poder consumir itens do inventário de quem tem a GUI aberta. */
    private final Inventory playerInventory;

    // Server
    public GuardEquipmentMenu(int id, Inventory playerInv, Guard guard) {
        this(id, playerInv, new GuardEquipmentContainer(guard), guard, serverStats(guard));
    }

    // Client
    public GuardEquipmentMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, new SimpleContainer(SLOTS.length),
                playerInv.player.level().getEntity(buf.readVarInt()) instanceof Guard g ? g : null,
                new SimpleContainerData(STAT_COUNT));
    }

    private GuardEquipmentMenu(int id, Inventory playerInv, Container equipment, @Nullable Guard guard,
                               ContainerData stats) {
        super(ModMenus.GUARD_EQUIPMENT.get(), id);
        checkContainerDataCount(stats, STAT_COUNT);
        this.equipment = equipment;
        this.guard = guard;
        this.stats = stats;
        this.playerInventory = playerInv;

        for (int i = 0; i < SLOTS.length; i++) {
            final EquipmentSlot es = SLOTS[i];
            final boolean armor = es.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
            this.addSlot(new Slot(equipment, i, POS[i][0], POS[i][1]) {
                @Override
                public int getMaxStackSize() {
                    return armor ? 1 : super.getMaxStackSize();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (!armor) {
                        return true;
                    }
                    Equipable e = Equipable.get(stack);
                    return e != null && e.getEquipmentSlot() == es;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        this.addDataSlots(stats);
    }

    private static ContainerData serverStats(Guard guard) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case STAT_HEALTH -> (int) Math.ceil(guard.getHealth());
                    case STAT_MAX_HEALTH -> Math.round(guard.getMaxHealth());
                    case STAT_ARMOR -> guard.getArmorValue();
                    case STAT_DAMAGE_X10 -> Math.round((float) guard.getAttributeValue(Attributes.ATTACK_DAMAGE) * 10.0F);
                    case STAT_LEVEL -> guard.getGuardLevel();
                    case STAT_XP -> guard.getGuardXp();
                    case STAT_UPGRADE_LEVEL -> guard.getUpgradeLevel();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return STAT_COUNT;
            }
        };
    }

    public int getHealth() {
        return this.stats.get(STAT_HEALTH);
    }

    public int getMaxHealth() {
        return this.stats.get(STAT_MAX_HEALTH);
    }

    public int getArmor() {
        return this.stats.get(STAT_ARMOR);
    }

    public float getDamage() {
        return this.stats.get(STAT_DAMAGE_X10) / 10.0F;
    }

    public int getGuardLevel() {
        return this.stats.get(STAT_LEVEL);
    }

    /** XP acumulado dentro do nível atual. */
    public int getGuardXp() {
        return this.stats.get(STAT_XP);
    }

    /** 0 = nenhum upgrade ainda; 1-3 = nível de upgrade atual (ver Guard.MAX_UPGRADE_LEVEL). */
    public int getUpgradeLevel() {
        return this.stats.get(STAT_UPGRADE_LEVEL);
    }

    @Nullable
    public Guard getGuard() {
        return guard;
    }

    private static int armorIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> -1;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < EQUIP_END) {
            if (!this.moveItemStackTo(stack, EQUIP_END, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = 4; // mainhand by default
            Equipable e = Equipable.get(stack);
            if (e != null && armorIndex(e.getEquipmentSlot()) >= 0) {
                target = armorIndex(e.getEquipmentSlot());
            }
            if (!this.moveItemStackTo(stack, target, target + 1, false)) {
                if (index < MAIN_END) {
                    if (!this.moveItemStackTo(stack, MAIN_END, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, EQUIP_END, MAIN_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /**
     * Chamado só do lado servidor (o cliente só envia o packet, quem executa é a instância
     * real deste menu no servidor — mesmo mecanismo do enchanting table / beacon). Tenta subir
     * o guard para o próximo nível de upgrade, consumindo os itens do inventário de quem tem a
     * GUI aberta. Devolve false (sem consumir nada) se faltarem itens ou já estiver no máximo.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_UPGRADE || this.guard == null) {
            return false;
        }
        return this.guard.tryUpgrade(this.playerInventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.equipment.stillValid(player);
    }
}