package com.marcpg.pillarperil;

import com.marcpg.libpg.lang.Translation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class TestTranslations {
    private static boolean loaded;

    private TestTranslations() {
    }

    static void ensure() {
        if (loaded) {
            return;
        }
        Map<String, String> entries = new HashMap<>();
        entries.put("scoreboard.mode", "Mode:");
        entries.put("scoreboard.name", "Name:");
        entries.put("scoreboard.time", "Time Left:");
        entries.put("scoreboard.kills", "Kills:");
        entries.put("info.end.force.title", "Game ended");
        entries.put("info.end.force.subtitle", "Forcefully stopped");
        entries.put("info.end.time-over.title", "Time's over!");
        entries.put("info.end.time-over.subtitle", "The limit has been reached");
        entries.put("info.end.time-over.stats", "Top {0} Players");
        entries.put("info.end.last-standing.title", "{0} wins!");
        entries.put("info.end.last-standing.subtitle", "With {0} kills!");
        entries.put("info.end.draw.title", "It's a draw!");
        entries.put("info.end.draw.subtitle", "No winners this time");

        Map<Locale, Map<String, String>> translations = new HashMap<>();
        translations.put(Locale.ENGLISH, entries);
        translations.put(Locale.US, entries);
        translations.put(Locale.getDefault(), entries);
        Translation.loadMaps(translations);
        loaded = true;
    }
}
