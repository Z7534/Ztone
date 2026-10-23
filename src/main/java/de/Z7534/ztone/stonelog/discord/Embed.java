package de.Z7534.ztone.stonelog.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Einfaches POJO fuer einen Discord-Embed. Wird von JsonUtil in das
 * Discord-Webhook-JSON-Format uebersetzt.
 */
public class Embed {

    public String authorName;
    public String authorIconUrl;
    public String title;
    public String description;
    public Integer color;
    public String footerText;

    public final List<Field> fields = new ArrayList<>();

    public Embed addField(String name, String value, boolean inline) {
        fields.add(new Field(name, value, inline));
        return this;
    }

    public static class Field {
        public final String name;
        public final String value;
        public final boolean inline;

        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }
}
