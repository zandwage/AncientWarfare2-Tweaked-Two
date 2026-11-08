package net.shadowmage.ancientwarfare.npc.client.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.shadowmage.ancientwarfare.npc.config.AWNPCStatics;

/**
 * Hides the HUD (hotbar, health, crosshair, chat, ...) while the cinematic dialogue screen is open.
 * Can be turned off with the dialogue_hide_hud client option.
 */
@SideOnly(Side.CLIENT)
public class DialogueHudHandler {

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
			return; //cancelling ALL would also skip our own screen's frame; cancel the individual pieces instead
		}
		if (Minecraft.getMinecraft().currentScreen instanceof GuiNpcDialogue && AWNPCStatics.shouldHideHudDuringDialogue()) {
			event.setCanceled(true);
		}
	}
}
