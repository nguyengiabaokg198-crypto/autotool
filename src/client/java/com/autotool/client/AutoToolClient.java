package com.autotool.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AutoTool: when the player starts hitting a block, silently select the best
 * matching tool from the hotbar (pickaxe/axe/shovel/hoe, sword for cobwebs and
 * other sword-efficient blocks). When attacking a mob, select a sword.
 * Pure client-side convenience - no packets, no server logic, not a cheat:
 * it only picks the slot, mining speed stays exactly vanilla.
 *
 * Toggle with the O key (remappable) - saved to config/autotool.properties.
 */
@Environment(EnvType.CLIENT)
public class AutoToolClient implements ClientModInitializer {
	public static final String MOD_ID = "autotool";

	private static boolean enabled = true;
	private static KeyMapping toggleKey;
	private static Path configFile;

	@Override
	public void onInitializeClient() {
		configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
		loadConfig();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.autotool.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O,
			KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				enabled = !enabled;
				saveConfig();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal(enabled
						? "[AutoTool] ĐÃ BẬT — đập block/quái là tự đổi tool đúng loại"
						: "[AutoTool] ĐÃ TẮT — bạn tự chọn tool như bình thường"));
				}
			}
		});

		AttackBlockCallback.EVENT.register(AutoToolClient::onAttackBlock);
		AttackEntityCallback.EVENT.register(AutoToolClient::onAttackEntity);
	}

	// ---------- block attacks ----------

	private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand,
	                                               BlockPos pos, Direction direction) {
		if (!enabled || hand != InteractionHand.MAIN_HAND || !(player instanceof LocalPlayer)) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return InteractionResult.PASS;
		}
		TagKey<Item> needed = blockTagFor(state);
		if (needed == null) {
			return InteractionResult.PASS;
		}
		selectBest(player.getInventory(), needed, true);
		return InteractionResult.PASS;
	}

	private static TagKey<Item> blockTagFor(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return ItemTags.PICKAXES;
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return ItemTags.AXES;
		}
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return ItemTags.SHOVELS;
		}
		if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			return ItemTags.HOES;
		}
		if (state.is(BlockTags.SWORD_EFFICIENT) || state.is(BlockTags.SWORD_INSTANTLY_MINES)) {
			return ItemTags.SWORDS;
		}
		return null;
	}

	// ---------- entity attacks (mobs -> sword) ----------

	private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand,
	                                                 Entity entity, EntityHitResult hitResult) {
		if (!enabled || hand != InteractionHand.MAIN_HAND || !(player instanceof LocalPlayer)) {
			return InteractionResult.PASS;
		}
		Inventory inv = player.getInventory();
		ItemStack held = inv.getSelectedItem();
		if (isTagged(held, ItemTags.SWORDS)) {
			return InteractionResult.PASS; // already holding a sword
		}
		selectBest(inv, ItemTags.SWORDS, false);
		return InteractionResult.PASS;
	}

	// ---------- helpers ----------

	/** Selects the highest-tier hotbar stack carrying the given item tag. */
	private static void selectBest(Inventory inv, TagKey<Item> needed, boolean skipIfAlreadyMatches) {
		ItemStack held = inv.getSelectedItem();
		if (skipIfAlreadyMatches && isTagged(held, needed)) {
			return; // nothing to do, avoid needless slot jumps
		}
		int best = -1;
		int bestDurability = -1;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (isTagged(stack, needed)) {
				int durability = stack.getMaxDamage();
				if (durability > bestDurability) {
					bestDurability = durability;
					best = i;
				}
			}
		}
		if (best >= 0) {
			inv.setSelectedSlot(best);
		}
	}

	private static boolean isTagged(ItemStack stack, TagKey<Item> tag) {
		return !stack.isEmpty() && stack.is(holder -> holder.is(tag));
	}

	// ---------- config ----------

	private static void loadConfig() {
		try {
			if (!Files.exists(configFile)) {
				return;
			}
			Properties props = new Properties();
			try (var in = Files.newInputStream(configFile)) {
				props.load(in);
			}
			enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
		} catch (Exception e) {
			// keep defaults
		}
	}

	private static void saveConfig() {
		try {
			Properties props = new Properties();
			props.setProperty("enabled", Boolean.toString(enabled));
			try (var out = Files.newOutputStream(configFile)) {
				props.store(out, "AutoTool config");
			}
		} catch (Exception e) {
			// non-fatal
		}
	}
}
