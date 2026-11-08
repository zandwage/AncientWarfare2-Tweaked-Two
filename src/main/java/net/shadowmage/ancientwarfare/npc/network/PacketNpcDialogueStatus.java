package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;

/*
 * client->server packet. Sent while the dialogue screen is open (as a keep-alive) and once more when it closes,
 * so that the NPC stands still and faces the player for as long as they are talking.
 */
public class PacketNpcDialogueStatus extends PacketBase {
	private static final double MAX_DISTANCE_SQ = 16D * 16D;

	private int entityId;
	private boolean talking;

	public PacketNpcDialogueStatus() {
		//needed for packet registration
	}

	public PacketNpcDialogueStatus(int entityId, boolean talking) {
		this.entityId = entityId;
		this.talking = talking;
	}

	@Override
	protected void writeToStream(ByteBuf data) {
		data.writeInt(entityId);
		data.writeBoolean(talking);
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		entityId = data.readInt();
		talking = data.readBoolean();
	}

	@Override
	protected void execute(EntityPlayer player) {
		Entity entity = player.world.getEntityByID(entityId);
		if (entity instanceof NpcFaction && entity.getDistanceSq(player) <= MAX_DISTANCE_SQ) {
			NpcFaction npc = (NpcFaction) entity;
			if (talking) {
				if (!npc.isHostileTowards(player)) {
					npc.beginOrRefreshDialogue(player);
				}
			} else {
				npc.endDialogue(player);
			}
		}
	}
}
