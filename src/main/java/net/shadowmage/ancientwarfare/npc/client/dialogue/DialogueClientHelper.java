package net.shadowmage.ancientwarfare.npc.client.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.shadowmage.ancientwarfare.npc.dialogue.DialogueOption;

import java.util.List;

/**
 * Keeps client-only code (the GuiScreen) out of the common packet class, so dedicated servers never try to load it.
 */
@SideOnly(Side.CLIENT)
public final class DialogueClientHelper {
	private DialogueClientHelper() {}

	public static void openDialogue(int entityId, String npcName, String factionName, String greeting, boolean hostile, List<DialogueOption> options) {
		Minecraft.getMinecraft().displayGuiScreen(new GuiNpcDialogue(entityId, npcName, factionName, greeting, hostile, options));
	}
}
