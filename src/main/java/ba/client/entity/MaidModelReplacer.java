package ba.client.entity;

import ba.client.render.entity.DealerSkinClient;
import minecraft.extended.tlm.client.render.entity.maid.MaidModelDispatcher;
import minecraft.extended.tlm.entity.maid.ProxyRenderMaid.MaidModelAsset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class MaidModelReplacer {
	/** kirriscasino 的荷官实体 */
	private static final ResourceLocation KIRRIS_DEALER_ID = ResourceLocation.fromNamespaceAndPath("kirriscasino", "dealer");

	private static final ResourceLocation KIRRIS_CASINO_NPC_ID = ResourceLocation.fromNamespaceAndPath("kirriscasino", "casino_npc");

	private static final String DEALER_YSM_MODEL = "BA 大阿罗娜会长.ysm";
	private static final String CASINO_NPC_YSM_MODEL = "";
	@SubscribeEvent
	public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
		MaidModelDispatcher.dispatch(EntityType.SLIME, (entity, model) -> {
			model.setTlmModelId(MaidModelAsset.hashTlmModelId(entity.getUUID()));
		});
		MaidModelDispatcher.dispatch(EntityType.WITHER, (entity, model) -> {
			model.setIsYsmModel(true);
			model.setYsmModelId("ba_白洲梓（泳装）.2.0.ysm");
		});
		MaidModelDispatcher.dispatch(EntityType.WARDEN, (entity, model) -> {
			model.setIsYsmModel(true);
			model.setYsmModelId("BA_空崎日奈：礼服.ysm");
		});
		MaidModelDispatcher.dispatch(EntityType.IRON_GOLEM, (entity, model) -> {
			model.setIsYsmModel(true);
			model.setYsmModelId(MaidModelAsset.hashLocalCustomYsmModelId(entity.getUUID()));
		});

		replaceCasinoEntities();
	}

	/**
	 * 把kirriscasino的荷官与剧情NPC换成YSM模型。<br>
	 * 该模组未安装时两个id都查不到，直接跳过，不产生硬依赖。
	 */
	private static void replaceCasinoEntities(){
		replaceDealer();
		replaceEntity(KIRRIS_CASINO_NPC_ID, CASINO_NPC_YSM_MODEL);
	}

	private static void replaceDealer(){
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(KIRRIS_DEALER_ID);
		if (type == null){
			return;
		}

		MaidModelDispatcher.dispatch(type, (entity, model) ->{
			String modelId = DealerSkinClient.pick(entity.getUUID());
			if (modelId == null){
				modelId = DEALER_YSM_MODEL;
			}

			if (modelId.isEmpty()){
				return;
			}
			model.setIsYsmModel(true);
			model.setYsmModelId(modelId);
		});
	}

	private static void replaceEntity(ResourceLocation entityID, String fixedYsmModelID){
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityID);
		if (type == null){
			return;
		}

		if (fixedYsmModelID.isEmpty() && MaidModelAsset.localCustomYsmModelIds().isEmpty()){
			return;
		}

		MaidModelDispatcher.dispatch(type, ((entity, model) -> {
			String modelId = fixedYsmModelID.isEmpty() ? MaidModelAsset.hashLocalCustomYsmModelId(entity.getUUID()) : fixedYsmModelID;
			if (modelId.isEmpty()){
				return;
			}
			model.setIsYsmModel(true);
			model.setYsmModelId(modelId);
		}));
	}
}
