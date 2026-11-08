package net.shadowmage.ancientwarfare.npc.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.shadowmage.ancientwarfare.core.config.ModConfiguration;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Loads the per-faction dialogue OPTIONS (the things the player can pick) from
 * config/ancientwarfare/npc_dialogue_options.json.
 * <p>
 * The file is generated with sensible defaults the first time the game runs. It is never overwritten once it
 * exists, so it is safe to edit by hand. If the file has a syntax error the built-in defaults are used
 * and the error is logged, the broken file is left alone so it can be fixed.
 * <p>
 * Greetings (the NPC's opening line) still come from the registry's dialogue.json, as before.
 */
public final class DialogueConfig {
	private static final String FILE_NAME = "npc_dialogue_options.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final String[] FALLBACK_FACTIONS = {"amazon", "barbarian", "beast", "brigand", "buffloka", "coven", "demon", "dwarf", "elf", "empire", "ent", "evil", "giant", "gnome", "good", "gremlin", "guild", "hobbit", "icelord", "ishtari", "klown", "kong", "lizardman", "mindflayer", "minossian", "monster", "nogg", "norska", "orc", "pirate", "rakshasa", "reiksgard", "sarkonid", "sealsker", "shakayana", "smingol", "undead", "vampire", "vyncan", "witchbane", "wizardly", "xoltec", "zamurai", "zimba"};

	private static final int MAX_OPTIONS_LIMIT = 50;
	private static final int MAX_RESPONSES_PER_OPTION = 100;

	private static volatile Data data;

	private DialogueConfig() {}

	/* ******************************************** JSON SHAPE ******************************************** */

	private static class Data {
		List<String> _readme = new ArrayList<>();
		boolean append_default_options = true;
		int max_options = 8;
		FactionOptions defaults = new FactionOptions();
		Map<String, FactionOptions> factions = new LinkedHashMap<>();
	}

	private static class FactionOptions {
		List<DialogueOption> friendly = new ArrayList<>();
		List<DialogueOption> hostile = new ArrayList<>();
	}

	/* ******************************************** LOADING ******************************************** */

	public static File getFile() {
		return new File(ModConfiguration.configPathForFiles, FILE_NAME);
	}

	/** (Re)loads the config from disk, creating the default file if it does not exist yet. */
	public static synchronized void load() {
		File file = getFile();
		if (!file.exists()) {
			Data defaults = buildDefaults();
			write(file, defaults);
			data = defaults;
			return;
		}

		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			if (loaded == null) {
				throw new JsonParseException("file is empty");
			}
			data = sanitize(loaded);
		}
		catch (IOException | JsonParseException e) {
			AncientWarfareNPC.LOG.error("Could not read {} - using built-in dialogue options instead. Fix the file (or delete it to regenerate) and restart the world. Error:", file.getPath(), e);
			data = buildDefaults();
		}
	}

	private static Data getData() {
		Data current = data;
		if (current == null) {
			load();
			current = data;
		}
		return current;
	}

	private static void write(File file, Data toWrite) {
		//noinspection ResultOfMethodCallIgnored
		file.getParentFile().mkdirs();
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			GSON.toJson(toWrite, writer);
		}
		catch (IOException e) {
			AncientWarfareNPC.LOG.error("Could not write default dialogue options to {}", file.getPath(), e);
		}
	}

	private static Data sanitize(Data d) {
		if (d.defaults == null) {
			d.defaults = new FactionOptions();
		}
		if (d.factions == null) {
			d.factions = new LinkedHashMap<>();
		}
		//the network packet stores these counts in a single byte, so keep them comfortably below 127
		d.max_options = Math.max(1, Math.min(d.max_options, MAX_OPTIONS_LIMIT));
		sanitize(d.defaults);
		d.factions.values().forEach(DialogueConfig::sanitize);
		return d;
	}

	private static void sanitize(FactionOptions fo) {
		if (fo == null) {
			return;
		}
		fo.friendly = sanitize(fo.friendly);
		fo.hostile = sanitize(fo.hostile);
	}

	private static List<DialogueOption> sanitize(List<DialogueOption> list) {
		List<DialogueOption> result = new ArrayList<>();
		if (list != null) {
			for (DialogueOption option : list) {
				if (option != null && option.sanitize().isValid()) {
					if (option.responses.size() > MAX_RESPONSES_PER_OPTION) {
						option.responses = new ArrayList<>(option.responses.subList(0, MAX_RESPONSES_PER_OPTION));
					}
					result.add(option);
				}
			}
		}
		return result;
	}

	/* ******************************************** QUERYING ******************************************** */

	/**
	 * Builds the list of options a given faction offers.
	 * Faction-specific options come first, followed by the shared defaults (when enabled or when the faction has none
	 * of its own). Any option that ends the conversation is moved to the bottom of the list, like "Goodbye" in Skyrim.
	 */
	public static List<DialogueOption> getOptions(String factionName, boolean hostile) {
		Data d = getData();
		FactionOptions factionOptions = d.factions.get(factionName);
		List<DialogueOption> specific = factionOptions == null ? Collections.emptyList() : (hostile ? factionOptions.hostile : factionOptions.friendly);
		if (specific == null) {
			specific = Collections.emptyList();
		}
		List<DialogueOption> shared = hostile ? d.defaults.hostile : d.defaults.friendly;

		List<DialogueOption> combined = new ArrayList<>(specific);
		if (specific.isEmpty() || d.append_default_options) {
			combined.addAll(shared);
		}

		List<DialogueOption> ordered = new ArrayList<>();
		List<DialogueOption> enders = new ArrayList<>();
		for (DialogueOption option : combined) {
			(option.end ? enders : ordered).add(option);
		}

		//trim the talking options (never the way out) so the list always fits on screen
		int room = Math.max(1, d.max_options - enders.size());
		while (ordered.size() > room) {
			ordered.remove(ordered.size() - 1);
		}
		ordered.addAll(enders);
		return ordered;
	}

	/* ******************************************** DEFAULTS ******************************************** */

	private static Data buildDefaults() {
		Data d = new Data();
		d._readme = Arrays.asList(
				"Dialogue options for faction NPCs. Edit freely - this file is only generated once and is never overwritten.",
				"'defaults' are offered by every faction. Entries under 'factions' are offered first, and the defaults come after them.",
				"Each faction has 'friendly' options (NPC likes you) and 'hostile' options (NPC is hostile to you). Leave a list empty to use only the defaults.",
				"Option fields: 'text' = what the player says. 'responses' = list of NPC replies, one is picked at random. 'end' = true closes the conversation.",
				"An 'end' option with replies prints one of them to chat as a parting line. Options with 'end': true are always shown last.",
				"You can use {npc} (NPC name), {player} (your name) and {faction} (faction name) inside any text or response.",
				"'append_default_options': false means a faction that has its own options will NOT also show the defaults (give it its own goodbye!).",
				"'max_options': the most options that will ever be shown at once (1 or more).",
				"Greeting lines (what the NPC says first) come from the registry's dialogue.json, not from this file.",
				"Invalid JSON makes the mod fall back to built-in defaults and print an error to the log. Delete this file to regenerate it.");
		d.append_default_options = true;
		d.max_options = 8;

		d.defaults.friendly.add(new DialogueOption("Who are you?", false,
				"I am {npc}, of the {faction}.",
				"They call me {npc}. I serve the {faction}.",
				"{npc}. The {faction} is my people, and this is our land."));
		d.defaults.friendly.add(new DialogueOption("Any news?", false,
				"Nothing worth repeating. Keep your eyes open on the road.",
				"The roads grow stranger by the day. Mind yourself out there.",
				"Rumors, mostly. None I would stake my life on."));
		d.defaults.friendly.add(new DialogueOption("Tell me about this place.", false,
				"It is home, and we mean to keep it that way.",
				"There are worse places to make a stand, and plenty of them nearby.",
				"Quiet, for now. We would like to keep it so."));
		d.defaults.friendly.add(new DialogueOption("Farewell.", true,
				"Safe travels, {player}.",
				"Until next time.",
				"Go well."));

		d.defaults.hostile.add(new DialogueOption("Stand down. I mean no harm.", false,
				"Your words mean nothing to me.",
				"Talk is cheap. Steel is not.",
				"I will not be fooled so easily."));
		d.defaults.hostile.add(new DialogueOption("I am leaving.", true,
				"See that you do.",
				"Go, before I change my mind."));

		d.factions.put("empire", friendly(
				new DialogueOption("What does the Empire want here?", false,
						"Order. Roads, walls and law - in that order.",
						"We build where others only raid. That is the difference between us."),
				new DialogueOption("Are you a soldier of the Empire?", false,
						"I serve as I am needed, citizen.")));
		d.factions.put("norska", friendly(
				new DialogueOption("How do you survive the cold?", false,
						"Fur, fire and stubbornness. Mostly stubbornness.",
						"You stop noticing it after the third winter. Then you stop noticing the fourth."),
				new DialogueOption("Do you raid?", false,
						"We trade. Sometimes the trading gets loud.")));
		d.factions.put("orc", friendly(
				new DialogueOption("Do you want a fight?", false,
						"Always. But you are not worth the trouble today.",
						"Ask me again when you have a bigger axe.")));
		d.factions.put("elf", friendly(
				new DialogueOption("How old are you?", false,
						"Older than the stones you are standing on, and wiser than your question.",
						"Age is not a number we count. It is a story we carry.")));
		d.factions.put("dwarf", friendly(
				new DialogueOption("What are you mining?", false,
						"Whatever the earth will give. And a few things she would rather not.",
						"Iron, mostly. But the good stuff is deeper, and so are the problems.")));
		d.factions.put("pirate", friendly(
				new DialogueOption("Where is your ship?", false,
						"Somewhere you cannot find it, landlubber.",
						"She is resting. Like me, she hates being asked questions.")));
		d.factions.put("undead", friendly(
				new DialogueOption("Do you remember who you were?", false,
						"...Fragments. Faces. None of them are mine anymore.",
						"Some of it. I try not to dwell on it.")));
		d.factions.put("hobbit", friendly(
				new DialogueOption("Is there anything to eat around here?", false,
						"Is there anything to EAT? Come in, come in, sit down!",
						"We have second breakfast, elevenses, and if you are lucky, a bit of cake.")));

		//make every other faction easy to find and fill in
		TreeSet<String> names = new TreeSet<>(FactionRegistry.getFactionNames());
		if (names.isEmpty()) {
			names.addAll(Arrays.asList(FALLBACK_FACTIONS));
		}
		for (String name : names) {
			d.factions.putIfAbsent(name, new FactionOptions());
		}
		d.factions = new LinkedHashMap<>(new TreeMap<>(d.factions)); //alphabetical, so the file is easy to navigate
		return d;
	}

	private static FactionOptions friendly(DialogueOption... options) {
		FactionOptions fo = new FactionOptions();
		fo.friendly.addAll(Arrays.asList(options));
		return fo;
	}
}
