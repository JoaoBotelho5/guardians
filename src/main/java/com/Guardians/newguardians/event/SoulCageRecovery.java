package com.Guardians.newguardians.event;


import com.Guardians.newguardians.client.data.PendingSoulsData;
import com.Guardians.newguardians.item.GuardCageItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte cages vazias "soul linked" em cages cheias assim que aparecem num sítio que o
 * servidor vê: inventário do jogador, qualquer menu aberto (vanilla ou mods), dentro de
 * shulkers, ou largadas no chão. Se não houver nenhuma alma pendente, não faz trabalho nenhum.
 */
public class SoulCageRecovery {

    private static final int CHECK_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.tickCount % CHECK_INTERVAL_TICKS == 0) {
            scanPlayer(player);
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scanMenu(player.server, event.getContainer());
        }
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity item
                && !item.level().isClientSide
                && item.tickCount % CHECK_INTERVAL_TICKS == 0) {
            MinecraftServer server = item.level().getServer();
            if (server == null || PendingSoulsData.get(server).isEmpty()) {
                return;
            }
            ItemStack replacement = process(server, item.getItem());
            if (replacement != null) {
                item.setItem(replacement);
            }
        }
    }

    /** Chamado logo na morte do guard, para o caso comum (cage no inventário / menu aberto). */
    public static void scanOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scanPlayer(player);
        }
    }

    private static void scanPlayer(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (PendingSoulsData.get(server).isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack replacement = process(server, inventory.getItem(i));
            if (replacement != null) {
                inventory.setItem(i, replacement);
            }
        }
        if (player.containerMenu != player.inventoryMenu) {
            scanMenu(server, player.containerMenu);
        }
    }

    private static void scanMenu(MinecraftServer server, AbstractContainerMenu menu) {
        if (PendingSoulsData.get(server).isEmpty()) {
            return;
        }
        for (Slot slot : menu.slots) {
            ItemStack replacement = process(server, slot.getItem());
            if (replacement != null) {
                slot.set(replacement);
                slot.setChanged();
            }
        }
    }

    /**
     * Devolve a stack que deve ficar no lugar da original (a cage cheia, ou a mesma shulker já
     * modificada), ou null se não houve nada para converter.
     */
    @Nullable
    private static ItemStack process(MinecraftServer server, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ItemStack soul = GuardCageItem.recoverSoul(server, stack);
        if (soul != null) {
            return soul;
        }

        // Shulkers e qualquer item com conteúdo (componente CONTAINER).
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return null;
        }
        boolean changed = false;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack inner : contents.stream().toList()) { // inclui slots vazios: mantém posições
            ItemStack replacement = process(server, inner);
            if (replacement != null) {
                items.add(replacement);
                changed = true;
            } else {
                items.add(inner.copy());
            }
        }
        if (!changed) {
            return null;
        }
        ItemStack modified = stack.copy();
        modified.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return modified;
    }
}