package ba.client.render.entity;

import ba.client.screen.DealerSkinScreen;
import ba.network.DealerSkinNetwork;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(value = Dist.CLIENT)
public final class DealerSkinClient {
    public static final List<String> SELECTED = new ArrayList<>();
    public static final List<String> MODELS = new ArrayList<>();

    private DealerSkinClient(){

    }

    public static List<String> models(){
        return new ArrayList<>(MODELS);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event){
        DealerSkinNetwork.CLIENT_HANDLER = DealerSkinClient::onServerMessage;
    }

    private static void onServerMessage(String content, IPayloadContext context){
        JsonObject root = DealerSkinNetwork.parse(content);
        String op = DealerSkinNetwork.readOp(root);
        if (DealerSkinNetwork.OP_OPEN.equals(op)){
            List<String> modelIds = DealerSkinNetwork.readIds(root);
            List<String> models = DealerSkinNetwork.readModels(root);
            Minecraft.getInstance().execute(() -> {
                MODELS.clear();
                MODELS.addAll(models);
                SELECTED.clear();
                SELECTED.addAll(modelIds);
                Minecraft.getInstance().setScreen(new DealerSkinScreen());
            });
        }else if (DealerSkinNetwork.OP_SYNC.equals(op)){
            List<String> models = DealerSkinNetwork.readModels(root);
            List<String> modelIds = DealerSkinNetwork.readIds(root);
            Minecraft.getInstance().execute(() -> {
                MODELS.clear();
                MODELS.addAll(models);
                SELECTED.clear();
                SELECTED.addAll(modelIds);
            });
        }
    }

    public static List<String> selected(){
        return new ArrayList<>(SELECTED);
    }

    public static boolean isSelected(String modelId){
        return SELECTED.contains(modelId);
    }

    public static String pick(UUID uuid){
        if (SELECTED.isEmpty()){
            return null;
        }
        return SELECTED.get(Math.floorMod(uuid.hashCode(), SELECTED.size()));
    }

    public static void save(List<String> modelIds){
        List<String> copy = new ArrayList<>(modelIds);
        SELECTED.clear();
        SELECTED.addAll(copy);
        DealerSkinNetwork.sendToServer(DealerSkinNetwork.encode(DealerSkinNetwork.OP_SAVE, copy, List.of()));
    }
}
