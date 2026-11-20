package com.ucsc.automation.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties from the classpath once.
 * Any key can be overridden from the command line, e.g. -Dbrowser=firefox -Dheadless=true
 */
public final class PropertyHandler {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream in = PropertyHandler.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new IllegalStateException(CONFIG_FILE + " not found on classpath");
            }
            PROPERTIES.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CONFIG_FILE, e);
        }
    }

    private PropertyHandler() {
    }

    public static String get(String key) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        if (value == null) {
            throw new IllegalArgumentException("Property '" + key + "' is not defined in " + CONFIG_FILE);
        }
        return value.trim();
    }

    public static String get(String key, String defaultValue) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        return value == null ? defaultValue : value.trim();
    }

    public static int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }
}
