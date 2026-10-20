package de.Z7534.ztone.stoneattack.commands;

import java.util.ArrayList;
import java.util.List;

/** Kleine Helfer, die in mehreren Command-Klassen gebraucht werden. */
public final class CommandUtil {

    private CommandUtil() {
    }

    public static List<String> filterStartingWith(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    public static boolean isInt(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
