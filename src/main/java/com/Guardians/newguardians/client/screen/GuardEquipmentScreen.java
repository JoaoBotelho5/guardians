package com.Guardians.newguardians.client.screen;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.entity.Guard;
import com.Guardians.newguardians.entity.GuardProgressionData;
import com.Guardians.newguardians.menu.GuardEquipmentMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public class GuardEquipmentScreen extends AbstractContainerScreen<GuardEquipmentMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(NewGuardiansMod.MODID, "textures/gui/guard_equipment.png");

    private static final int LABEL_COLOR = 0x404040;
    private static final int STATS_X = 102;
    private static final int BAR_X = 102;
    private static final int BAR_Y = 58;
    private static final int BAR_WIDTH = 66;
    private static final int BAR_HEIGHT = 5;

    // Espaço por cima dos slots de mainhand/offhand ({77,62} e {77,44}): ícone do item que falta
    // para o próximo upgrade, e o botão "UP" por baixo dele.
    private static final int UPGRADE_ICON_X = 77;
    private static final int UPGRADE_ICON_Y = 8;
    private static final int UPGRADE_BUTTON_X = 75;
    private static final int UPGRADE_BUTTON_Y = 27;
    private static final int UPGRADE_BUTTON_WIDTH = 20;
    private static final int UPGRADE_BUTTON_HEIGHT = 14;

    @Nullable
    private Button upgradeButton;

    public GuardEquipmentScreen(GuardEquipmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.upgradeButton = this.addRenderableWidget(Button.builder(Component.literal("UP"), button ->
                        // Só envia o pedido; quem decide se há itens suficientes e aplica o
                        // upgrade é o clickMenuButton do lado servidor (GuardEquipmentMenu).
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, GuardEquipmentMenu.BUTTON_UPGRADE))
                .bounds(this.leftPos + UPGRADE_BUTTON_X, this.topPos + UPGRADE_BUTTON_Y,
                        UPGRADE_BUTTON_WIDTH, UPGRADE_BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderUpgradeTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        Guard guard = this.menu.getGuard();
        if (guard != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    this.leftPos + 26, this.topPos + 8, this.leftPos + 75, this.topPos + 78,
                    30, 0.0625F, mouseX, mouseY, guard);
        }
        renderUpgradeIcon(graphics);
    }

    /** Mostra o item (com a quantidade) que falta para o próximo upgrade; nada se já estiver no máximo. */
    private void renderUpgradeIcon(GuiGraphics graphics) {
        int upgradeLevel = this.menu.getUpgradeLevel();
        if (this.upgradeButton != null) {
            this.upgradeButton.visible = upgradeLevel < Guard.MAX_UPGRADE_LEVEL;
        }
        if (upgradeLevel >= Guard.MAX_UPGRADE_LEVEL) {
            return;
        }
        ItemStack requirement = nextUpgradeRequirement(upgradeLevel);
        int x = this.leftPos + UPGRADE_ICON_X;
        int y = this.topPos + UPGRADE_ICON_Y;
        graphics.renderItem(requirement, x, y);
        graphics.renderItemDecorations(this.font, requirement, x, y);
    }

    /** Descrição rápida do que este upgrade dá, ao passar o rato pelo ícone. */
    private void renderUpgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int upgradeLevel = this.menu.getUpgradeLevel();
        if (upgradeLevel >= Guard.MAX_UPGRADE_LEVEL) {
            return;
        }
        int x = this.leftPos + UPGRADE_ICON_X;
        int y = this.topPos + UPGRADE_ICON_Y;
        if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) {
            return;
        }
        ItemStack requirement = nextUpgradeRequirement(upgradeLevel);
        List<Component> lines = new java.util.ArrayList<>(getTooltipFromItem(this.minecraft, requirement));
        lines.add(Component.literal(upgradeDescription(upgradeLevel + 1))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        graphics.renderTooltip(this.font, lines, requirement.getTooltipImage(), mouseX, mouseY);
    }

    private static ItemStack nextUpgradeRequirement(int currentUpgradeLevel) {
        Item item = Guard.UPGRADE_ITEMS[currentUpgradeLevel];
        int count = Guard.UPGRADE_COSTS[currentUpgradeLevel];
        return new ItemStack(item, count);
    }

    private static String upgradeDescription(int upgradeLevelBeingBought) {
        return switch (upgradeLevelBeingBought) {
            case 1 -> "+10 Max HP";
            case 2 -> "Permanent Resistance I";
            case 3 -> "+3 HP regen";
            default -> "";
        };
    }

    // Coordenadas relativas ao canto superior esquerdo da GUI
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font,
                "HP: " + this.menu.getHealth() + "/" + this.menu.getMaxHealth(),
                STATS_X, 10, LABEL_COLOR, false);
        graphics.drawString(this.font,
                "Armor: " + this.menu.getArmor(),
                STATS_X, 22, LABEL_COLOR, false);
        graphics.drawString(this.font,
                String.format("DMG: %.1f", this.menu.getDamage()),
                STATS_X, 34, LABEL_COLOR, false);
        graphics.drawString(this.font,
                "Level: " + this.menu.getGuardLevel(),
                STATS_X, 46, LABEL_COLOR, false);

        // Barra de XP até ao próximo nível
        int xp = this.menu.getGuardXp();
        int required = GuardProgressionData.xpRequiredForLevel(this.menu.getGuardLevel());
        int filled = required > 0 ? Math.min(BAR_WIDTH, BAR_WIDTH * xp / required) : 0;
        graphics.fill(BAR_X - 1, BAR_Y - 1, BAR_X + BAR_WIDTH + 1, BAR_Y + BAR_HEIGHT + 1, 0xFF373737);
        graphics.fill(BAR_X, BAR_Y, BAR_X + BAR_WIDTH, BAR_Y + BAR_HEIGHT, 0xFF1C1C1C);
        if (filled > 0) {
            graphics.fill(BAR_X, BAR_Y, BAR_X + filled, BAR_Y + BAR_HEIGHT, 0xFF80FF20);
        }
        graphics.drawString(this.font, xp + "/" + required + " XP", STATS_X, 66, LABEL_COLOR, false);
    }
}