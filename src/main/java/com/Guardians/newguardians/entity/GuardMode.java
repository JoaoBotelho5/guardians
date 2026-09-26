package com.Guardians.newguardians.entity;

public enum GuardMode {
    FOLLOWING("Following"),
    STAYING("Staying"),
    WANDERING("Wandering");

    private final String displayName;

    GuardMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Following -> Staying -> Wandering -> Following ... */
    public GuardMode next() {
        GuardMode[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    public static GuardMode byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return WANDERING;
        }
    }
}