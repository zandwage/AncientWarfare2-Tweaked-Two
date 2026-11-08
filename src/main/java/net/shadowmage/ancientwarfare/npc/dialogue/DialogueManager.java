package net.shadowmage.ancientwarfare.npc.dialogue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.translation.I18n;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.network.PacketNpcDialogue;
import net.shadowmage.ancientwarfare.npc.registry.NPCDialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Server-side entry point for starting a conversation with a faction NPC.
 */
public final class DialogueManager {
	private DialogueManager() {}

	/**
	 * Resolves the NPC's greeting and the options for its faction, then tells the player's client to open the
	 * cinematic dialogue screen.
	 */
	public static void startDialogue(EntityPlayer player, NpcFaction npc) {
		if (!(player instanceof EntityPlayerMP)) {
			return;
		}
		boolean hostile = npc.isHostileTowards(player);
		String faction = npc.getFaction();
		String npcName = npc.getName();

		UnaryOperator<String> tokens = text -> replaceTokens(text, npcName, player.getName(), faction);

		String greeting = tokens.apply(NPCDialogue.getGreeting(faction, hostile, npc.dialogueSeed));
		List<DialogueOption> options = new ArrayList<>();
		for (DialogueOption option : DialogueConfig.getOptions(faction, hostile)) {
			options.add(option.copyWithTokens(tokens));
		}

		if (!hostile) {
			npc.beginOrRefreshDialogue(player);
		}
		NetworkHandler.sendToPlayer((EntityPlayerMP) player, new PacketNpcDialogue(npc.getEntityId(), npcName, faction, greeting, hostile, options));
	}

	private static String replaceTokens(String text, String npcName, String playerName, String faction) {
		return text.replace("{npc}", npcName).replace("{player}", playerName).replace("{faction}", getFactionDisplayName(faction));
	}

	@SuppressWarnings("deprecation") //same server-side-safe I18n call that NpcFaction.getName uses
	private static String getFactionDisplayName(String faction) {
		String key = "gui.ancientwarfarenpc.faction_name." + faction;
		String translated = I18n.translateToLocal(key);
		if (!translated.equals(key)) {
			return translated;
		}
		return faction.isEmpty() ? faction : Character.toUpperCase(faction.charAt(0)) + faction.substring(1);
	}
}
