package com.reservedslots.network;

import com.reservedslots.ReservedSlotsMod;
import com.reservedslots.common.ReservedSlotData;
import com.reservedslots.common.SlotState;
import com.reservedslots.server.ReservedSlotManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles all network communication for reserved slots.
 */
public class ReservedSlotPackets {
    // Packet IDs
    public static final Identifier TOGGLE_SLOT_ID = Identifier.of(ReservedSlotsMod.MOD_ID, "toggle_slot");
    public static final Identifier SYNC_SLOT_ID = Identifier.of(ReservedSlotsMod.MOD_ID, "sync_slot");
    public static final Identifier FULL_SYNC_ID = Identifier.of(ReservedSlotsMod.MOD_ID, "full_sync");

    /**
     * Packet sent from client to server to toggle a slot's state.
     */
    public record ToggleSlotPayload(int slotIndex) implements CustomPayload {
        public static final CustomPayload.Id<ToggleSlotPayload> ID = new CustomPayload.Id<>(TOGGLE_SLOT_ID);
        public static final PacketCodec<RegistryByteBuf, ToggleSlotPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ToggleSlotPayload::slotIndex,
            ToggleSlotPayload::new
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Packet sent from server to client to sync a single slot's state.
     */
    public record SyncSlotPayload(int slotIndex, SlotState state, Identifier itemId) implements CustomPayload {
        public static final CustomPayload.Id<SyncSlotPayload> ID = new CustomPayload.Id<>(SYNC_SLOT_ID);
        public static final PacketCodec<RegistryByteBuf, SyncSlotPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncSlotPayload::slotIndex,
            PacketCodecs.indexed(i -> SlotState.values()[i], SlotState::ordinal), SyncSlotPayload::state,
            Identifier.PACKET_CODEC, SyncSlotPayload::itemId,
            SyncSlotPayload::new
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Packet sent from server to client to sync all slot states.
     */
    public record FullSyncPayload(Map<Integer, SlotData> slots) implements CustomPayload {
        public static final CustomPayload.Id<FullSyncPayload> ID = new CustomPayload.Id<>(FULL_SYNC_ID);
        
        public record SlotData(SlotState state, Identifier itemId) {
            public static final PacketCodec<RegistryByteBuf, SlotData> CODEC = PacketCodec.tuple(
                PacketCodecs.indexed(i -> SlotState.values()[i], SlotState::ordinal), SlotData::state,
                Identifier.PACKET_CODEC, SlotData::itemId,
                SlotData::new
            );
        }
        
        public static final PacketCodec<RegistryByteBuf, FullSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.slots.size());
                value.slots.forEach((index, data) -> {
                    buf.writeVarInt(index);
                    SlotData.CODEC.encode(buf, data);
                });
            },
            buf -> {
                int size = buf.readVarInt();
                Map<Integer, SlotData> slots = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    int index = buf.readVarInt();
                    SlotData data = SlotData.CODEC.decode(buf);
                    slots.put(index, data);
                }
                return new FullSyncPayload(slots);
            }
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Registers all network packets.
     */
    public static void register() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(ToggleSlotPayload.ID, ToggleSlotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncSlotPayload.ID, SyncSlotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FullSyncPayload.ID, FullSyncPayload.CODEC);

        // Server-side handler for toggle requests
        ServerPlayNetworking.registerGlobalReceiver(ToggleSlotPayload.ID, (payload, context) -> {
            ReservedSlotsMod.LOGGER.info("Server received toggle request for slot {}", payload.slotIndex());
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                ReservedSlotsMod.LOGGER.info("Processing toggle for player {}", player.getName().getString());
                ReservedSlotManager.toggleSlot(player, payload.slotIndex());
            });
        });
    }

    /**
     * Registers client-side packet handlers.
     */
    public static void registerClient() {
        ReservedSlotsMod.LOGGER.info("Registering client packet handlers");
        
        // Client-side handler for slot sync
        ClientPlayNetworking.registerGlobalReceiver(SyncSlotPayload.ID, (payload, context) -> {
            ReservedSlotsMod.LOGGER.info("Received SyncSlot packet: slot={}, state={}, item={}", payload.slotIndex(), payload.state(), payload.itemId());
            context.client().execute(() -> {
                ClientSlotDataCache.setSlotData(
                    payload.slotIndex(),
                    payload.state(),
                    payload.itemId()
                );
            });
        });

        // Client-side handler for full sync
        ClientPlayNetworking.registerGlobalReceiver(FullSyncPayload.ID, (payload, context) -> {
            ReservedSlotsMod.LOGGER.info("Received FullSync packet: {} slots", payload.slots().size());
            context.client().execute(() -> {
                ClientSlotDataCache.clear();
                payload.slots().forEach((index, data) -> {
                    ClientSlotDataCache.setSlotData(index, data.state(), data.itemId());
                    ReservedSlotsMod.LOGGER.info("  Slot {}: {} - {}", index, data.state(), data.itemId());
                });
            });
        });
    }

    /**
     * Sends a toggle request from client to server.
     */
    public static void sendToggleRequest(int slotIndex) {
        ReservedSlotsMod.LOGGER.info("Sending toggle request for slot {}", slotIndex);
        ClientPlayNetworking.send(new ToggleSlotPayload(slotIndex));
        ReservedSlotsMod.LOGGER.info("Toggle request sent successfully");
    }

    /**
     * Sends a slot sync packet from server to client.
     */
    public static void sendSyncPacket(ServerPlayerEntity player, int slotIndex, ReservedSlotData data) {
        Identifier itemId = data.getReservedItem() != null 
            ? Registries.ITEM.getId(data.getReservedItem())
            : Identifier.of("minecraft:air");
        
        ServerPlayNetworking.send(player, new SyncSlotPayload(slotIndex, data.getState(), itemId));
    }

    /**
     * Sends a full sync packet from server to client.
     */
    public static void sendFullSyncPacket(ServerPlayerEntity player, Map<Integer, ReservedSlotData> allData) {
        Map<Integer, FullSyncPayload.SlotData> syncData = new HashMap<>();
        
        allData.forEach((index, data) -> {
            if (data.getState() != SlotState.NORMAL) {
                Identifier itemId = data.getReservedItem() != null
                    ? Registries.ITEM.getId(data.getReservedItem())
                    : Identifier.of("minecraft:air");
                syncData.put(index, new FullSyncPayload.SlotData(data.getState(), itemId));
            }
        });
        
        if (!syncData.isEmpty()) {
            ServerPlayNetworking.send(player, new FullSyncPayload(syncData));
        }
    }
}
