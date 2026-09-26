package com.Guardians.newguardians.menu;

import com.Guardians.newguardians.entity.Guard;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GuardEquipmentContainer implements Container {

    private final Guard guard;

    public GuardEquipmentContainer(Guard guard) {
        this.guard = guard;
    }

    @Override
    public int getContainerSize() {
        return GuardEquipmentMenu.SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return guard.getItemBySlot(GuardEquipmentMenu.SLOTS[index]);
    }

    @Override
    public ItemStack removeItem(int index, int amount) {
        ItemStack current = getItem(index);
        if (current.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = current.split(amount);
        setItem(index, current);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = getItem(index);
        setItem(index, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        guard.setItemSlot(GuardEquipmentMenu.SLOTS[index], stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return guard.isAlive() && player.distanceToSqr(guard) < 64.0D && guard.isOwnedBy(player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, ItemStack.EMPTY);
        }
    }
}