package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.client.dialogue.DialogueClientHelper;
import net.shadowmage.ancientwarfare.npc.dialogue.DialogueOption;

import java.util.ArrayList;
import java.util.List;

/*
 * server->client packet. Tells the client to open the cinematic dialogue screen for an NPC.
 * The server resolves all text (greeting, options, replies) so that a dedicated server's config is what everyone sees.
 */
public class PacketNpcDialogue extends PacketBase {
	private int entityId;
	private String npcName = "";
	private String factionName = "";
	private String greeting = "";
	private boolean hostile;
	private List<DialogueOption> options = new ArrayList<>();

	public PacketNpcDialogue() {
		//needed for packet registration
	}

	public PacketNpcDialogue(int entityId, String npcName, String factionName, String greeting, boolean hostile, List<DialogueOption> options) {
		this.entityId = entityId;
		this.npcName = npcName;
		this.factionName = factionName;
		this.greeting = greeting;
		this.hostile = hostile;
		this.options = options;
	}

	@Override
	protected void writeToStream(ByteBuf data) {
		data.writeInt(entityId);
		ByteBufUtils.writeUTF8String(data, npcName);
		ByteBufUtils.writeUTF8String(data, factionName);
		ByteBufUtils.writeUTF8String(data, greeting);
		data.writeBoolean(hostile);
		data.writeByte(options.size());
		for (DialogueOption option : options) {
			ByteBufUtils.writeUTF8String(data, option.text);
			data.writeBoolean(option.end);
			data.writeByte(option.responses.size());
			for (String response : option.responses) {
				ByteBufUtils.writeUTF8String(data, response);
			}
		}
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		entityId = data.readInt();
		npcName = ByteBufUtils.readUTF8String(data);
		factionName = ByteBufUtils.readUTF8String(data);
		greeting = ByteBufUtils.readUTF8String(data);
		hostile = data.readBoolean();
		int optionCount = data.readByte();
		options = new ArrayList<>(optionCount);
		for (int i = 0; i < optionCount; i++) {
			DialogueOption option = new DialogueOption();
			option.text = ByteBufUtils.readUTF8String(data);
			option.end = data.readBoolean();
			int responseCount = data.readByte();
			for (int r = 0; r < responseCount; r++) {
				option.responses.add(ByteBufUtils.readUTF8String(data));
			}
			options.add(option);
		}
	}

	@Override
	protected void execute(EntityPlayer player) {
		//only ever executed on the client; the helper keeps client-only classes out of this (common) class
		DialogueClientHelper.openDialogue(entityId, npcName, factionName, greeting, hostile, options);
	}
}
