package com.reservedslots.network;

import com.reservedslots.ReservedSlotsMod;
import com.reservedslots.common.ReservedSlotData;
import com.reservedslots.common.SlotState;
import com.reservedslots.server.ReservedSlotManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles all network communication for reserved slots.
 */
public class ReservedSlotPackets {
    // Packet IDs
    public static final Identifier TOGGLE_SLOT_ID = Identifier.fromNamespaceAndPath(ReservedSlotsMod.MOD_ID, "toggle_slot");
    public static final Identifier SYNC_SLOT_ID = Identifier.fromNamespaceAndPath(ReservedSlotsMod.MOD_ID, "sync_slot");
    public static final Identifier FULL_SYNC_ID = Identifier.fromNamespaceAndPath(ReservedSlotsMod.MOD_ID, "full_sync");

    /**
     * Packet sent from client to server to toggle a slot's state.
     */
    public record ToggleSlotPayload(int slotIndex) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ToggleSlotPayload> ID = new CustomPacketPayload.Type<>(TOGGLE_SLOT_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSlotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToggleSlotPayload::slotIndex,
            ToggleSlotPayload::new
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Packet sent from server to client to sync a single slot's state.
     */
    public record SyncSlotPayload(int slotIndex, SlotState state, Identifier itemId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncSlotPayload> ID = new CustomPacketPayload.Type<>(SYNC_SLOT_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncSlotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncSlotPayload::slotIndex,
            ByteBufCodecs.VAR_INT.map(i -> SlotState.values()[i], SlotState::ordinal), SyncSlotPayload::state,
            Identifier.STREAM_CODEC, SyncSlotPayload::itemId,
            SyncSlotPayload::new
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Packet sent from server to client to sync all slot states.
     */
    public record FullSyncPayload(Map<Integer, SlotData> slots) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FullSyncPayload> ID = new CustomPacketPayload.Type<>(FULL_SYNC_ID);
        
        public record SlotData(SlotState state, Identifier itemId) {
            public static final StreamCodec<RegistryFriendlyByteBuf, SlotData> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(i -> SlotState.values()[i], SlotState::ordinal), SlotData::state,
                Identifier.STREAM_CODEC, SlotData::itemId,
                SlotData::new
            );
        }
        
        public static final StreamCodec<RegistryFriendlyByteBuf, FullSyncPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
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
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Registers all network packets.
     */
    public static void register() {
        // Register payload types
        PayloadTypeRegistry.serverboundPlay().register(ToggleSlotPayload.ID, ToggleSlotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncSlotPayload.ID, SyncSlotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullSyncPayload.ID, FullSyncPayload.CODEC);

        // Server-side handler for toggle requests
        ServerPlayNetworking.registerGlobalReceiver(ToggleSlotPayload.ID, (payload, context) -> {
            ReservedSlotsMod.LOGGER.info("Server received toggle request for slot {}", payload.slotIndex());
            context.server().execute(() -> {
                ServerPlayer player = context.player();
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
    public static void sendSyncPacket(ServerPlayer player, int slotIndex, ReservedSlotData data) {
        Identifier itemId = data.getReservedItem() != null 
            ? BuiltInRegistries.ITEM.getKey(data.getReservedItem())
            : Identifier.fromNamespaceAndPath("minecraft", "air");
        
        ServerPlayNetworking.send(player, new SyncSlotPayload(slotIndex, data.getState(), itemId));
    }

    /**
     * Sends a full sync packet from server to client.
     */
    public static void sendFullSyncPacket(ServerPlayer player, Map<Integer, ReservedSlotData> allData) {
        Map<Integer, FullSyncPayload.SlotData> syncData = new HashMap<>();
        
        allData.forEach((index, data) -> {
            if (data.getState() != SlotState.NORMAL) {
                Identifier itemId = data.getReservedItem() != null
                    ? BuiltInRegistries.ITEM.getKey(data.getReservedItem())
                    : Identifier.fromNamespaceAndPath("minecraft", "air");
                syncData.put(index, new FullSyncPayload.SlotData(data.getState(), itemId));
            }
        });
        
        if (!syncData.isEmpty()) {
            ServerPlayNetworking.send(player, new FullSyncPayload(syncData));
        }
    }
}
