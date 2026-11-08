package net.shadowmage.ancientwarfare.npc.client.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.config.AWNPCStatics;
import net.shadowmage.ancientwarfare.npc.dialogue.DialogueOption;
import net.shadowmage.ancientwarfare.npc.network.PacketNpcDialogueStatus;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Skyrim-style dialogue screen.
 * <ul>
 * <li>Cinematic bars slide onto the top and bottom of the screen (height / speed / color are configurable).</li>
 * <li>The NPC's line is shown above a list of options. Mouse wheel, Up/Down or W/S cycle through them,
 * Enter, Space or a click picks one, number keys jump straight to an option, Esc leaves.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class GuiNpcDialogue extends GuiScreen {
	private static final float BAR_ANIMATION_BASE_MS = 500F;
	private static final int MAX_VISIBLE_OPTIONS = 6;
	private static final int KEEP_ALIVE_INTERVAL_TICKS = 10;
	private static final double MAX_TALK_DISTANCE_SQ = 10D * 10D;

	private static final int NAME_COLOR = 0xFFFF55;
	private static final int HOSTILE_NAME_COLOR = 0xFF5555;
	private static final int LINE_COLOR = 0xFFFFFF;
	private static final int SELECTED_COLOR = 0xFFFFFF;
	private static final int UNSELECTED_COLOR = 0x9A9A9A;
	private static final int HINT_COLOR = 0x8A8A8A;

	private final Random random = new Random();
	private final int npcEntityId;
	private final String npcName;
	private final boolean hostile;
	private final List<DialogueOption> options = new ArrayList<>();

	private String npcLine;
	private int selected = 0;
	private int scrollOffset = 0;

	//animation state
	private float barProgress = 0F; //0 = bars off screen, 1 = fully in
	private boolean closing = false;
	private boolean finished = false;
	private long lastFrameMs = -1L;
	private long lineStartMs = -1L;

	//input / bookkeeping
	private int lastMouseX = -1;
	private int lastMouseY = -1;
	private int keepAliveTicks = 0;
	private boolean closeStatusSent = false;
	private final List<int[]> optionRects = new ArrayList<>(); //{x1, y1, x2, y2, optionIndex}

	public GuiNpcDialogue(int npcEntityId, String npcName, String factionName, String greeting, boolean hostile, List<DialogueOption> options) {
		this.npcEntityId = npcEntityId;
		this.npcName = npcName;
		this.hostile = hostile;
		this.npcLine = greeting;
		this.options.addAll(options);
		if (this.options.isEmpty()) {
			this.options.add(new DialogueOption("Farewell.", true));
		}
	}

	/* ******************************************** LIFECYCLE ******************************************** */

	@Override
	public boolean doesGuiPauseGame() {
		return false; //the world keeps running (and the dialogue closes itself if the player is attacked)
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if (finished) {
			if (mc.currentScreen == this) {
				mc.displayGuiScreen(null);
			}
			return;
		}
		if (closing) {
			return;
		}

		Entity npc = getNpc();
		EntityPlayerSP player = mc.player;
		if (npc == null || npc.isDead || player == null || player.isDead || player.getDistanceSq(npc) > MAX_TALK_DISTANCE_SQ || player.hurtTime > 0) {
			beginClose();
			return;
		}

		if (++keepAliveTicks >= KEEP_ALIVE_INTERVAL_TICKS) {
			keepAliveTicks = 0;
			sendStatus(true);
		}
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		sendCloseStatus();
	}

	private void beginClose() {
		if (!closing) {
			closing = true;
			sendCloseStatus();
		}
	}

	private void sendCloseStatus() {
		if (!closeStatusSent) {
			closeStatusSent = true;
			sendStatus(false);
		}
	}

	/** Tells the server whether we are (still) talking, so the NPC knows to stand still. Skipped if we're disconnecting. */
	private void sendStatus(boolean talking) {
		if (mc != null && mc.player != null && mc.getConnection() != null) {
			NetworkHandler.sendToServer(new PacketNpcDialogueStatus(npcEntityId, talking));
		}
	}

	private Entity getNpc() {
		return mc.world == null ? null : mc.world.getEntityByID(npcEntityId);
	}

	/* ******************************************** INPUT ******************************************** */

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (closing) {
			return;
		}
		if (keyCode == Keyboard.KEY_ESCAPE || mc.gameSettings.keyBindInventory.isActiveAndMatches(keyCode)) {
			beginClose();
		} else if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_W) {
			moveSelection(-1);
		} else if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_S) {
			moveSelection(1);
		} else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE) {
			chooseOption(selected);
		} else if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
			int index = keyCode - Keyboard.KEY_1;
			if (index < options.size()) {
				selected = index;
				chooseOption(index);
			}
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0 && !closing) {
			moveSelection(wheel > 0 ? -1 : 1);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (closing || mouseButton != 0) {
			return;
		}
		for (int[] rect : optionRects) {
			if (isInside(mouseX, mouseY, rect)) {
				selected = rect[4];
				chooseOption(rect[4]);
				return;
			}
		}
	}

	private static boolean isInside(int x, int y, int[] rect) {
		return x >= rect[0] && x < rect[2] && y >= rect[1] && y < rect[3];
	}

	/** Cycles through the options, wrapping around at either end. */
	private void moveSelection(int delta) {
		int count = options.size();
		selected = ((selected + delta) % count + count) % count;
	}

	private void chooseOption(int index) {
		if (index < 0 || index >= options.size()) {
			return;
		}
		DialogueOption option = options.get(index);
		String reply = option.responses.isEmpty() ? null : option.responses.get(random.nextInt(option.responses.size()));

		if (option.end) {
			if (reply != null && mc.player != null) {
				mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[" + npcName + "] " + TextFormatting.WHITE + reply));
			}
			beginClose();
			return;
		}
		if (reply != null) {
			npcLine = reply;
			lineStartMs = -1L; //restarts the typewriter on the next frame
		}
	}

	/* ******************************************** DRAWING ******************************************** */

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		long now = Minecraft.getSystemTime();
		if (lastFrameMs < 0L) {
			lastFrameMs = now;
		}
		if (lineStartMs < 0L) {
			lineStartMs = now;
		}
		float deltaMs = Math.min(100L, Math.max(0L, now - lastFrameMs));
		lastFrameMs = now;

		animateBars(deltaMs);
		if (finished) {
			return;
		}
		float eased = barProgress * barProgress * (3F - 2F * barProgress); //smoothstep
		if (!closing && AWNPCStatics.shouldFaceNpcDuringDialogue()) {
			faceNpc(deltaMs);
		}

		int barColor = 0xFF000000 | AWNPCStatics.getDialogueBarColor();
		int fullBarPx = Math.round(height * AWNPCStatics.getDialogueBarHeightPercent() / 100F);
		int barPx = Math.round(fullBarPx * eased);
		int textAlpha = (int) (255F * eased * eased);

		//layout (all in scaled gui coordinates), text sits just above where the bottom bar will end up
		int lineHeight = fontRenderer.FONT_HEIGHT;
		int rowHeight = lineHeight + 5;
		int textWidth = Math.min(width - 40, Math.max(200, (int) (width * 0.55F)));
		int left = (width - textWidth) / 2;

		List<String> wrapped = fontRenderer.listFormattedStringToWidth(npcLine, textWidth);
		int visibleOptions = Math.min(options.size(), MAX_VISIBLE_OPTIONS);
		keepSelectionVisible(visibleOptions);

		int bottomEdge = height - fullBarPx - 8;
		int optionsTop = bottomEdge - visibleOptions * rowHeight;
		int linesTop = optionsTop - 10 - wrapped.size() * lineHeight;
		int nameY = linesTop - lineHeight - 4;

		//work out where each option row is, and let the mouse hover select them (only when the mouse actually moved)
		optionRects.clear();
		for (int row = 0; row < visibleOptions; row++) {
			int index = scrollOffset + row;
			int y = optionsTop + row * rowHeight;
			optionRects.add(new int[] {left, y, left + textWidth, y + rowHeight, index});
		}
		if (mouseX != lastMouseX || mouseY != lastMouseY) {
			lastMouseX = mouseX;
			lastMouseY = mouseY;
			if (!closing) {
				for (int[] rect : optionRects) {
					if (isInside(mouseX, mouseY, rect)) {
						selected = rect[4];
						break;
					}
				}
			}
		}

		//soft dark backdrop so text stays readable against any scenery
		int gradientTop = Math.max(0, nameY - 30);
		drawGradientRect(0, gradientTop, width, height, 0x00000000, ((int) (0xB0 * eased) << 24));

		//the cinematic bars themselves
		if (barPx > 0) {
			drawRect(0, 0, width, barPx, barColor);
			drawRect(0, height - barPx, width, height, barColor);
		}

		if (textAlpha > 8) {
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
			drawNpcText(wrapped, left, textWidth, nameY, linesTop, lineHeight, textAlpha, now);
			drawOptions(left, textWidth, rowHeight, visibleOptions, textAlpha);
			drawHint(barPx, fullBarPx, lineHeight, textAlpha);
		}
	}

	private void animateBars(float deltaMs) {
		float durationMs = BAR_ANIMATION_BASE_MS / AWNPCStatics.getDialogueBarSpeed();
		float step = deltaMs / durationMs;
		if (closing) {
			barProgress = Math.max(0F, barProgress - step);
			if (barProgress <= 0F) {
				finished = true; //the actual screen swap happens in updateScreen, not mid-render
			}
		} else {
			barProgress = Math.min(1F, barProgress + step);
		}
	}

	/** Smoothly turns the player's camera towards the NPC's head. */
	private void faceNpc(float deltaMs) {
		Entity npc = getNpc();
		EntityPlayerSP player = mc.player;
		if (npc == null || player == null) {
			return;
		}
		double dx = npc.posX - player.posX;
		double dz = npc.posZ - player.posZ;
		double dy = (npc.posY + npc.getEyeHeight()) - (player.posY + player.getEyeHeight());

		float targetYaw = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
		float targetPitch = (float) (-(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180D / Math.PI));

		float factor = 1F - (float) Math.exp(-deltaMs / 150F);
		player.rotationYaw += MathHelper.wrapDegrees(targetYaw - player.rotationYaw) * factor;
		player.rotationPitch = MathHelper.clamp(player.rotationPitch + (targetPitch - player.rotationPitch) * factor, -90F, 90F);
		player.prevRotationYaw = player.rotationYaw; //keeps the camera from jittering between frames
		player.prevRotationPitch = player.rotationPitch;
	}

	private void keepSelectionVisible(int visibleOptions) {
		if (selected < scrollOffset) {
			scrollOffset = selected;
		} else if (selected >= scrollOffset + visibleOptions) {
			scrollOffset = selected - visibleOptions + 1;
		}
		scrollOffset = Math.max(0, Math.min(scrollOffset, options.size() - visibleOptions));
	}

	private void drawNpcText(List<String> wrapped, int left, int textWidth, int nameY, int linesTop, int lineHeight, int alpha, long now) {
		int centerX = left + textWidth / 2;

		String name = npcName;
		fontRenderer.drawStringWithShadow(name, centerX - fontRenderer.getStringWidth(name) / 2F, nameY, withAlpha(hostile ? HOSTILE_NAME_COLOR : NAME_COLOR, alpha));

		//typewriter: reveal the line character by character (0 chars/second = instantly)
		int charsPerSecond = AWNPCStatics.getDialogueTypewriterSpeed();
		long revealed = charsPerSecond == 0 ? Long.MAX_VALUE : (now - lineStartMs) * charsPerSecond / 1000L;
		int y = linesTop;
		for (String lineText : wrapped) {
			if (revealed <= 0) {
				break;
			}
			String shown = lineText.length() <= revealed ? lineText : lineText.substring(0, (int) revealed);
			fontRenderer.drawStringWithShadow(shown, centerX - fontRenderer.getStringWidth(lineText) / 2F, y, withAlpha(LINE_COLOR, alpha));
			revealed -= lineText.length();
			y += lineHeight;
		}
	}

	private void drawOptions(int left, int textWidth, int rowHeight, int visibleOptions, int alpha) {
		int centerX = left + textWidth / 2;
		for (int[] rect : optionRects) {
			int index = rect[4];
			boolean isSelected = index == selected;
			if (isSelected) {
				int highlight = ((alpha * 0x40 / 255) << 24) | 0xFFFFFF;
				drawRect(rect[0], rect[1], rect[2], rect[3], highlight);
			}
			String label = (index + 1) + ". " + options.get(index).text;
			int color = withAlpha(isSelected ? SELECTED_COLOR : UNSELECTED_COLOR, alpha);
			fontRenderer.drawStringWithShadow(label, centerX - fontRenderer.getStringWidth(label) / 2F, rect[1] + (rowHeight - fontRenderer.FONT_HEIGHT) / 2F + 1F, color);
		}

		//little arrows when the list scrolls
		int arrowColor = withAlpha(HINT_COLOR, alpha);
		if (scrollOffset > 0) {
			fontRenderer.drawStringWithShadow("^", left + textWidth - 8, optionRects.get(0)[1] + 1, arrowColor);
		}
		if (scrollOffset + visibleOptions < options.size()) {
			fontRenderer.drawStringWithShadow("v", left + textWidth - 8, optionRects.get(visibleOptions - 1)[1] + 1, arrowColor);
		}
	}

	/** Controls reminder, tucked inside the bottom bar when that bar is tall enough to hold it. */
	private void drawHint(int barPx, int fullBarPx, int lineHeight, int alpha) {
		if (fullBarPx < lineHeight + 6) {
			return;
		}
		String hint = I18n.format("gui.ancientwarfarenpc.dialogue.hint");
		int y = height - barPx + (barPx - lineHeight) / 2;
		fontRenderer.drawStringWithShadow(hint, (width - fontRenderer.getStringWidth(hint)) / 2F, y, withAlpha(HINT_COLOR, alpha));
	}

	/** FontRenderer treats very low alpha as fully opaque, so callers skip drawing when alpha is tiny. */
	private static int withAlpha(int rgb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}
}
