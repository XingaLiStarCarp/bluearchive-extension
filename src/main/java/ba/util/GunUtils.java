package ba.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import minecraft.core.registry.MappedRegistryAccess;
import minecraft.extended.tacz.TaczGuns;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

public class GunUtils {

	private static final Map<String, GunTabType> TYPE_NAME_MAP = new HashMap<>();
	private static EnumMap<GunTabType, List<String>> GUN_CACHE = null;
	private static Map<AttachmentType, List<String>> ATTACHMENT_CACHE = null;

	private static final Random RANDOM = new Random();

	private static final Logger LOGGER = Logger.getLogger("SCBA_GunUtils");

	// 初始化枪械类型名称映射
	static {
		for (GunTabType type : GunTabType.values()) {
			TYPE_NAME_MAP.put(type.name().toLowerCase(Locale.ROOT), type);
		}
	}

	// 获取所有枪械
	public static EnumMap<GunTabType, List<String>> getAllGuns() {
		if (GUN_CACHE != null) {
			return GUN_CACHE;
		}

		EnumMap<GunTabType, List<String>> map = new EnumMap<>(GunTabType.class);
		for (GunTabType type : GunTabType.values()) {
			map.put(type, new ArrayList<>());
		}

		var allGuns = TimelessAPI.getAllCommonGunIndex();
		for (var entry : allGuns) {
			ResourceLocation id = entry.getKey();
			CommonGunIndex index = entry.getValue();
			String typeStr = index.getType();

			GunTabType type = TYPE_NAME_MAP.get(typeStr);
			if (type != null) {
				map.get(type).add(id.toString());
			}

		}
		GUN_CACHE = map;
		return map;
	}

	// 根据枪械类型随机获取枪械
	public static String getRandomGun(GunTabType type) {
		var allGuns = getAllGuns();
		List<String> guns = allGuns.get(type);
		if (guns == null || guns.isEmpty()) {
			return null;
		}

		return guns.get(RANDOM.nextInt(guns.size()));
	}

	public static Optional<GunData> getGunData(ResourceLocation gunId) {
		return TimelessAPI.getCommonGunIndex(gunId).map(CommonGunIndex::getGunData);
	}

	public static List<AttachmentType> getAllowedAttachmentTypes(ResourceLocation gunId) {
		return getGunData(gunId).map(GunData::getAllowAttachments).orElse(Collections.emptyList());
	}

	public static Map<AttachmentType, List<String>> getAvailableAttachments(ResourceLocation gunId) {
		List<AttachmentType> allowedTypes = getAllowedAttachmentTypes(gunId);
		if (allowedTypes.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<AttachmentType, List<String>> result = new EnumMap<>(AttachmentType.class);
		for (AttachmentType type : allowedTypes) {
			result.put(type, new ArrayList<>());
		}

		var allAttachments = TimelessAPI.getAllCommonAttachmentIndex();
		for (var entry : allAttachments) {
			ResourceLocation id = entry.getKey();
			CommonAttachmentIndex index = entry.getValue();
			AttachmentType type = index.getType();

			if (type == null) {
				LOGGER.warning("Skipping attachment {} because its type is null! (Check the gun pack JSON)");
				continue;
			}

			if (!allowedTypes.contains(type))
				continue;

			result.get(type).add(id.toString());
		}

		return result;
	}

	// 获取枪械配件并装配
	public static void equipRandomAttachments(ItemStack gunStack, ResourceLocation gunId) {
		IGun gun = IGun.getIGunOrNull(gunStack);
		if (gun == null){
			return;
		}

		Map<AttachmentType, List<String>> available = getAvailableAttachments(gunId);
		if (available.isEmpty()){
			return;
		}

		HolderLookup.Provider provider = MappedRegistryAccess.serverRegistryAccess;
		if (provider == null){
			LOGGER.warning("serverRegistryAccess is not initialized, skipping accessory generation");
			return;
		}

		RandomSource random = RandomSource.create();

		for (Map.Entry<AttachmentType, List<String>> entry : available.entrySet()){
			AttachmentType type = entry.getKey();
			List<String> pool = entry.getValue();
			if (type == null || pool == null || pool.isEmpty()){
				continue;
			}

			if (!DefaultAssets.isEmptyAttachmentId(gun.getAttachmentId(gunStack, type))){
				continue;
			}

			if (random.nextFloat() >= 0.5f){
				continue;
			}

			String id = pool.get(random.nextInt(pool.size()));
			gun.installAttachment(provider, gunStack, AttachmentItemBuilder.create().setId(ResourceLocation.parse(id)).build());
		}
	}

	/**
	 * 构造指定类型的随机ID的枪（附带随机配件）
	 * 
	 * @param gun
	 * @return
	 */
	public static ItemStack getRandomGunWithAttachments(GunTabType type) {
		String gun = getRandomGun(type);
		if (gun == null)
			return ItemStack.EMPTY;
		return getRandomGunWithAttachments(gun);
	}

	/**
	 * 构造指定ID的枪（附带随机配件）
	 * 
	 * @param gun
	 * @return
	 */
	public static ItemStack getRandomGunWithAttachments(String gun) {
		ItemStack gunStack = TaczGuns.getGun(gun);
		equipRandomAttachments(gunStack, ResourceLocation.parse(gun));
		return gunStack;
	}
}
