package com.Guardians.newguardians.client;

import com.Guardians.newguardians.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * O cooldown da soul cage cheia é guardado por stack (NBT), não pelo cooldown partilhado do
 * Item, por isso o vanilla já não desenha sozinho o "swipe" de cooldown em cima dela. Esta
 * classe substitui isso em todos os sítios onde a cage pode aparecer: hotbar, offhand, e
 * qualquer slot de qualquer inventário aberto (do jogador, de um baú, desta própria mod, etc.).
 */
public class GuardCageCooldownOverlay implements LayeredDraw.Layer {

    private static final String GUARD_COOLDOWN_END_KEY = "GuardCooldownEnd";
    private static final int SOUL_COOLDOWN_TICKS = 12000;

    // Mesmas contas que o vanilla usa para posicionar os ícones da hotbar/offhand.
    private static final int SLOT_SIZE = 20;
    private static final int ICON_INSET = 3;
    private static final int ICON_SIZE = 16;
    private static final int OFFHAND_LEFT_OF_HOTBAR = -29;
    private static final int OFFHAND_RIGHT_OF_HOTBAR = 170;

    // ------------------------------------------------------------- hotbar + offhand

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.options.hideGui) {
            return;
        }

        Inventory inventory = player.getInventory();
        int left = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 19;

        for (int i = 0; i < 9; i++) {
            drawCooldownIfNeeded(graphics, inventory.getItem(i), level, left + i * SLOT_SIZE + ICON_INSET, y);
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            // A offhand aparece do lado oposto à mão dominante do jogador, tal como no vanilla.
            int offhandSlotLeft = left + (player.getMainArm() == HumanoidArm.LEFT
                    ? OFFHAND_RIGHT_OF_HOTBAR : OFFHAND_LEFT_OF_HOTBAR);
            drawCooldownIfNeeded(graphics, offhand, level, offhandSlotLeft + ICON_INSET, y);
        }
    }

    // ------------------------------------------------------------- qualquer inventário aberto

    /**
     * Corre para qualquer ecrã de container aberto (inventário do jogador, baús, esta própria
     * GuardEquipmentScreen, etc.): percorre todos os slots do menu e desenha o overlay em cima
     * de qualquer soul cage cheia ainda em cooldown que esteja lá dentro.
     */
    @SubscribeEvent
    public void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        for (Slot slot : screen.getMenu().slots) {
            drawCooldownIfNeeded(event.getGuiGraphics(), slot.getItem(), level, slot.x, slot.y);
        }
    }

    // ------------------------------------------------------------- desenho comum

    private void drawCooldownIfNeeded(GuiGraphics graphics, ItemStack stack, Level level, int x, int y) {
        if (!stack.is(ModItems.GUARD_CAGE_FILLED.get())) {
            return;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(GUARD_COOLDOWN_END_KEY)) {
            return;
        }

        long remainingTicks = tag.getLong(GUARD_COOLDOWN_END_KEY) - level.getGameTime();
        if (remainingTicks <= 0) {
            return;
        }

        float percent = Mth.clamp(remainingTicks / (float) SOUL_COOLDOWN_TICKS, 0.0F, 1.0F);
        int filledFromTop = Mth.floor(ICON_SIZE * (1.0F - percent));
        // guiOverlay ignora o depth test do item já desenhado, senão o retângulo ficava escondido atrás do ícone.
        graphics.fill(RenderType.guiOverlay(), x, y + filledFromTop, x + ICON_SIZE, y + ICON_SIZE, 0x80000000);
    }
}