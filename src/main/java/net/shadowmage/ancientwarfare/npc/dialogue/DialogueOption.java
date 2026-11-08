package net.shadowmage.ancientwarfare.npc.dialogue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One selectable line in the Skyrim-style dialogue menu.
 * <p>
 * This class is both the in-memory representation sent to the client and the JSON shape used in
 * config/ancientwarfare/npc_dialogue_options.json, so field names here ARE the JSON keys.
 */
public class DialogueOption {
	/** What the player "says" - the text shown in the option list. */
	public String text = "";
	/** Possible NPC replies. One is picked at random when this option is chosen. May be empty. */
	public List<String> responses = new ArrayList<>();
	/** If true, choosing this option closes the conversation (the reply, if any, is printed to chat). */
	public boolean end = false;

	public DialogueOption() {
		//needed by Gson
	}

	public DialogueOption(String text, boolean end, String... responses) {
		this.text = text;
		this.end = end;
		this.responses = new ArrayList<>(Arrays.asList(responses));
	}

	/** Makes sure nothing is null after Gson has been at it, since hand-edited files can omit keys. */
	public DialogueOption sanitize() {
		if (text == null) {
			text = "";
		}
		if (responses == null) {
			responses = new ArrayList<>();
		}
		responses.removeIf(r -> r == null || r.isEmpty());
		return this;
	}

	public boolean isValid() {
		return text != null && !text.trim().isEmpty();
	}

	public DialogueOption copyWithTokens(java.util.function.UnaryOperator<String> replacer) {
		DialogueOption copy = new DialogueOption();
		copy.text = replacer.apply(text);
		copy.end = end;
		for (String response : responses) {
			copy.responses.add(replacer.apply(response));
		}
		return copy;
	}
}
