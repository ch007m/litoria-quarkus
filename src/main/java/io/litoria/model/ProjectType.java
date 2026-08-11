package io.litoria.model;

public enum ProjectType {
    SIMPLE("simple"),
    REPORT("report"),
    SLIDESHOW("slideshow");

    private final String value;

    ProjectType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProjectType fromString(String text) {
        for (ProjectType pt : values()) {
            if (pt.value.equalsIgnoreCase(text)) {
                return pt;
            }
        }
        return SIMPLE;
    }
}
