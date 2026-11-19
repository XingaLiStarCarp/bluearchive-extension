package ba.network;

import com.google.gson.*;
import minecraft.core.Core;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@EventBusSubscriber
public final class DealerSkinServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<String> SELECTED = new ArrayList<>();

    private static final boolean INCLUDE_BUILTIN = true;

    private static final String YSM_ROOT = "yes_steve_model";
    private static final String MODEL_JSON = "ysm.json";
    private static final String MODEL_EXTENSION = ".ysm";

    private static final Pattern NOT_DISPLAYED = Pattern.compile("(?m)^\\s*ClientNotDisplayModels\\s*=\\s*\\[(.*?)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    private static boolean loaded = false;

    private DealerSkinServer(){

    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event){
        NeoForge.EVENT_BUS.addListener(DealerSkinServer::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DealerSkinServer::onPlayerLoggedIn);
    }

    private static Path file(){
        return Core.config("ba_dealer_skins.json");
    }

    public static List<String> selected(){
        load();
        return new ArrayList<>(SELECTED);
    }

    private static void load(){
        if (loaded){
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)){
            return;
        }
        try {
            JsonElement rootElement = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!rootElement.isJsonObject()){
                return;
            }
            List<String> modelIds = DealerSkinNetwork.readIds(rootElement.getAsJsonObject());
            SELECTED.clear();
            SELECTED.addAll(modelIds);
        }catch (Exception ex){
            Core.logWarn("read Dealer Skin Config failure " + ex);
        }
    }

    private static void save(){
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (String modelId : SELECTED){
            array.add(modelId);
        }
        root.add("selected", array);
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }catch (IOException ex){
            Core.logWarn("write Dealer Skin Config failure" + ex);
        }
    }

    private static List<String> modelIds(){
        List<String> modelIds  = new ArrayList<>();
        collectCustom(Core.config(YSM_ROOT + "/custom"), modelIds);
        if (INCLUDE_BUILTIN){
            collectBuiltin(Core.config(YSM_ROOT + "/builtin"), modelIds);
        }
        modelIds.removeAll(notDisplayedModels());
        modelIds.sort(String.CASE_INSENSITIVE_ORDER);
        return modelIds;
    }

    private static void collectCustom(Path dir, List<String> modelIds){
        if (!Files.isDirectory(dir)){
            return;
        }
        try (Stream<Path> stream = Files.list(dir)){
            stream.forEach(path -> {
                String name = path.getFileName().toString();
                if (Files.isDirectory(path)){
                    addModelId(modelIds, name);
                }else if (name.toLowerCase(Locale.ROOT).endsWith(MODEL_EXTENSION)){
                    addModelId(modelIds, name);
                }
            });
        }catch (IOException ex){
            Core.logWarn("scan custom model dir failure " + ex);
        }
    }

    private static void collectBuiltin(Path dir, List<String> modelIds){
        if (!Files.isDirectory(dir)){
            return;
        }
        try (Stream<Path> packs = Files.list(dir)){
            packs.filter(Files::isDirectory).forEach(pack ->{
                if (Files.isRegularFile(pack.resolve(MODEL_JSON))){
                    addModelId(modelIds, pack.getFileName().toString());
                    return;
                }
                try (Stream<Path> models = Files.list(pack)){
                    String packName = pack.getFileName().toString();
                    models.filter(Files::isDirectory).filter(model -> Files.isRegularFile(model.resolve(MODEL_JSON))).forEach(model -> addModelId(modelIds, packName + "_" + model.getFileName()));
                }catch (IOException ex){
                    Core.logWarn("scan builtin model dir failure " + ex);
                }
            });
        }catch (IOException ex){
            Core.logWarn("scan builtin model dir failure " + ex);
        }
    }

    private static void addModelId(List<String> modelIds, String modelId){
        if (!modelId.isEmpty() && !modelIds.contains(modelId)){
            modelIds.add(modelId);
        }
    }

    private static List<String> notDisplayedModels(){
        List<String> hidden = new ArrayList<>();
        Path path = Core.config("yes_steve_model-server.toml");
        if (!Files.isRegularFile(path)){
            return hidden;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = NOT_DISPLAYED.matcher(text);
            if (!matcher.find()){
                return hidden;
            }
            Matcher quoted = QUOTED.matcher(matcher.group(1));
            while (quoted.find()){
                String modelId = quoted.group(1).trim();
                if (!modelId.isEmpty() && !hidden.contains(modelId)){
                    hidden.add(modelId);
                }
            }
        }catch (Exception ex){
            Core.logWarn("read yes_steve_model-server.toml failure " + ex);
        }
        return hidden;
    }

    public static void onRegisterCommands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ba")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dealerskin")
                        .executes(context ->{
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null){
                                return 0;
                            }
                            DealerSkinNetwork.sendToPlayer(player, DealerSkinNetwork.encode(DealerSkinNetwork.OP_OPEN, selected(), modelIds()));
                            return 1;
                        })
                )
        );
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event){
        if (event.getEntity() instanceof ServerPlayer player){
            DealerSkinNetwork.sendToPlayer(player, DealerSkinNetwork.encode(DealerSkinNetwork.OP_SYNC, selected(), modelIds()));
        }
    }

    public static void onClientMessage(String content, IPayloadContext context){
        JsonObject root = DealerSkinNetwork.parse(content);
        if (!DealerSkinNetwork.OP_SAVE.equals(DealerSkinNetwork.readOp(root))){
            return;
        }
        List<String> modelIds = DealerSkinNetwork.readIds(root);
        context.enqueueWork(() ->{
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)){
                return;
            }
            load();
            SELECTED.clear();
            SELECTED.addAll(modelIds);
            save();
            DealerSkinNetwork.broadcast(player.server, DealerSkinNetwork.encode(DealerSkinNetwork.OP_SYNC, SELECTED, modelIds()));
        });
    }
}
