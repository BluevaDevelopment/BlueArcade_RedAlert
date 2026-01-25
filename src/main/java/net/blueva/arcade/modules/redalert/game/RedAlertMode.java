package net.blueva.arcade.modules.redalert.game;

public enum RedAlertMode {
    CHAOS("chaos", "Chaos Grid"),
    TRAIL("trail", "Trail Collapse");

    private final String key;
    private final String display;

    RedAlertMode(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String getKey() {
        return key;
    }

    public String getDisplay() {
        return display;
    }

    public static RedAlertMode fromString(String value) {
        if (value == null) {
            return CHAOS;
        }
        for (RedAlertMode mode : values()) {
            if (mode.key.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return CHAOS;
    }
}
