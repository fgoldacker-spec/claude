package de.goldacker.notruf;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Stored settings: emergency contacts (in call order), own name and SMS text. */
final class Prefs {
    static final String DEFAULT_MESSAGE = "NOTFALL! Ich brauche dringend Hilfe. Bitte melde dich sofort!";

    static final class Contact {
        final String name;
        final String number;

        Contact(String name, String number) {
            this.name = name == null ? "" : name.trim();
            this.number = number;
        }

        String label() {
            return name.isEmpty() ? number : name + " (" + number + ")";
        }
    }

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("notruf", Context.MODE_PRIVATE);
    }

    /** One contact per line, "number\tname". */
    static List<Contact> contacts(Context c) {
        List<Contact> list = new ArrayList<>();
        for (String line : sp(c).getString("contacts", "").split("\n")) {
            if (line.isEmpty()) continue;
            int tab = line.indexOf('\t');
            if (tab < 0) list.add(new Contact("", line));
            else list.add(new Contact(line.substring(tab + 1), line.substring(0, tab)));
        }
        return list;
    }

    static void setContacts(Context c, List<Contact> list) {
        StringBuilder sb = new StringBuilder();
        for (Contact k : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k.number).append('\t').append(k.name.replace('\n', ' ').replace('\t', ' '));
        }
        sp(c).edit().putString("contacts", sb.toString()).apply();
    }

    static String name(Context c) {
        return sp(c).getString("name", "");
    }

    static String message(Context c) {
        String m = sp(c).getString("message", "").trim();
        return m.isEmpty() ? DEFAULT_MESSAGE : m;
    }

    static void setNameAndMessage(Context c, String name, String message) {
        sp(c).edit().putString("name", name.trim()).putString("message", message.trim()).apply();
    }

    /** Keeps digits, a leading '+' and the dial characters '*' and '#'. */
    static String cleanNumber(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.trim().toCharArray()) {
            if (Character.isDigit(ch) || ch == '*' || ch == '#' || (ch == '+' && sb.length() == 0)) sb.append(ch);
        }
        return sb.toString();
    }
}
