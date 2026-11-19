package ba.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import minecraft.network.StringPacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@EventBusSubscriber
public final class DealerSkinNetwork {
    public static final String OP_OPEN = "open";
    public static final String OP_SYNC = "sync";
    public static final String OP_SAVE = "save";
    public static final String KEY_SELECTED = "selected";
    public static final String KEY_MODELS = "models";

    public static final int Max_SELECTED = 64;
    public static final int Max_MODELS = 512;
    public static final int Max_ID_LENGTH = 256;

    public static BiConsumer<String, IPayloadContext> CLIENT_HANDLER;

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event){
        PayloadRegistrar registrar = event.registrar(StringPacket.PROTOCOL_VERSION);
        StringPacket.register(registrar);
        registrar.playBidirectional(StringPacket.TYPE, StringPacket.STREAM_CODEC,
                (packet, context) ->{
            if (context.flow() == PacketFlow.SERVERBOUND){
                DealerSkinServer.onClientMessage(packet.content, context);
                return;
            }
            BiConsumer<String, IPayloadContext> handler = CLIENT_HANDLER;
            if (handler != null){
                handler.accept(packet.content, context);
            }
                });
    }

    public static String encode(String op, List<String> modelIds, List<String> models){
        JsonObject root = new JsonObject();
        root.addProperty("op", op);
        root.add(KEY_SELECTED, toArray(modelIds));
        root.add(KEY_MODELS, toArray(models));
        return root.toString();
    }

    private static JsonArray toArray(List<String> values){
        JsonArray array = new JsonArray();
        if (values != null){
            for (String value : values){
                array.add(value);
            }
        }
        return array;
    }

    public static JsonObject parse(String content){
        try {
            JsonElement element = JsonParser.parseString(content);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }catch (Exception ex){
            return null;
        }
    }

    public static String readOp(JsonObject root){
        if (root == null || !root.has("op") || !root.get("op").isJsonPrimitive()){
            return "";
        }
        return root.get("op").getAsString();
    }

    public static List<String> readIds(JsonObject root){
        return readIds(root, KEY_SELECTED, Max_SELECTED);
    }

    public static List<String> readModels(JsonObject root){
        return readIds(root, KEY_MODELS, Max_MODELS);
    }

    private static List<String> readIds(JsonObject root, String key, int max){
        List<String> modelIDs = new ArrayList<>();
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()){
            return modelIDs;
        }
        for (JsonElement element : root.getAsJsonArray(key)){
            if (element == null || !element.isJsonPrimitive()){
                continue;
            }
            String modelId = element.getAsString().trim();
            if (modelId.isEmpty() || modelId.length() > Max_ID_LENGTH || modelIDs.contains(modelId) || modelIDs.size() >= max){
                continue;
            }
            modelIDs.add(modelId);
        }
        return modelIDs;
    }

    public static void sendToPlayer(ServerPlayer player, String content){
        StringPacket.sendToPlayer(player, content);
    }

    public static void sendToServer(String content){
        StringPacket.sendToServer(content);
    }

    public static void broadcast(MinecraftServer server, String content){
        if (server == null){
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()){
            sendToPlayer(player, content);
        }
    }

}
