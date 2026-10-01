package com.Guardians.newguardians.client.data;

import com.Guardians.newguardians.NewGuardiansMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Almas de guards mortos à espera de a cage vazia ligada aparecer num sítio acessível. */
public class PendingSoulsData extends SavedData {

    private static final String NAME = NewGuardiansMod.MODID + "_pending_souls";

    private final Map<UUID, CompoundTag> souls = new HashMap<>();

    public static PendingSoulsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PendingSoulsData::new, PendingSoulsData::load, null), NAME);
    }

    public boolean isEmpty() {
        return souls.isEmpty();
    }

    public void put(UUID linkId, CompoundTag soulData) {
        souls.put(linkId, soulData);
        setDirty();
    }

    /** Devolve e remove a alma (null se não houver). */
    @Nullable
    public CompoundTag take(UUID linkId) {
        CompoundTag data = souls.remove(linkId);
        if (data != null) {
            setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        souls.forEach((id, data) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.put("Data", data.copy());
            list.add(entry);
        });
        tag.put("Souls", list);
        return tag;
    }

    private static PendingSoulsData load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingSoulsData data = new PendingSoulsData();
        ListTag list = tag.getList("Souls", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.souls.put(entry.getUUID("Id"), entry.getCompound("Data"));
        }
        return data;
    }
}