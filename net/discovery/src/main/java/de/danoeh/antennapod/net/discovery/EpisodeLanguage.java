package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

import java.util.Locale;

public enum EpisodeLanguage {
    SYSTEM("system", "", "", ""),
    ALL("all", "", "", ""),
    GERMAN("de", "de", "DE", "de_de"),
    ENGLISH("en", "en", "US", "en_us"),
    FRENCH("fr", "fr", "FR", "fr_fr"),
    SPANISH("es", "es", "ES", "es_es"),
    ITALIAN("it", "it", "IT", "it_it"),
    DUTCH("nl", "nl", "NL", "nl_nl"),
    PORTUGUESE("pt", "pt", "PT", "pt_pt"),
    POLISH("pl", "pl", "PL", "pl_pl"),
    SWEDISH("sv", "sv", "SE", "sv_se"),
    TURKISH("tr", "tr", "TR", "tr_tr"),
    RUSSIAN("ru", "ru", "RU", "ru_ru"),
    JAPANESE("ja", "ja", "JP", "ja_jp"),
    ARABIC("ar", "ar", "SA", "ar_sa"),
    CHINESE("zh", "zh", "CN", "zh_cn");

    private final String storageKey;
    private final String isoLanguage;
    private final String countryCode;
    private final String itunesLang;

    EpisodeLanguage(String storageKey, String isoLanguage, String countryCode, String itunesLang) {
        this.storageKey = storageKey;
        this.isoLanguage = isoLanguage;
        this.countryCode = countryCode;
        this.itunesLang = itunesLang;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public static EpisodeLanguage fromStorageKey(@Nullable String key) {
        if (key == null || key.trim().isEmpty()) {
            return SYSTEM;
        }
        for (EpisodeLanguage value : values()) {
            if (value.storageKey.equalsIgnoreCase(key.trim())) {
                return value;
            }
        }
        return SYSTEM;
    }

    public String getResolvedIsoLanguage() {
        if (this == ALL) {
            return "";
        }
        if (this == SYSTEM) {
            String lang = Locale.getDefault().getLanguage();
            return lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        }
        return isoLanguage;
    }

    public String getResolvedCountryCode() {
        if (this == ALL) {
            return "";
        }
        if (this == SYSTEM) {
            String country = Locale.getDefault().getCountry();
            if (country != null && country.length() == 2) {
                return country.toUpperCase(Locale.ROOT);
            }
            return defaultCountryForLanguage(getResolvedIsoLanguage());
        }
        return countryCode;
    }

    public String getResolvedItunesLang() {
        if (this == ALL) {
            return "";
        }
        if (this == SYSTEM) {
            String lang = getResolvedIsoLanguage();
            String country = getResolvedCountryCode();
            if (lang.isEmpty() || country.isEmpty()) {
                return "";
            }
            return lang + "_" + country.toLowerCase(Locale.ROOT);
        }
        return itunesLang;
    }

    public String getResolvedCacheKey() {
        if (this == ALL) {
            return "all";
        }
        return getResolvedIsoLanguage() + "_" + getResolvedCountryCode();
    }

    public String getLocalizedLanguageName(Locale displayLocale) {
        String code = getResolvedIsoLanguage();
        if (code.isEmpty()) {
            return "";
        }
        String name = new Locale(code).getDisplayLanguage(displayLocale);
        if (name.isEmpty()) {
            return code.toUpperCase(displayLocale);
        }
        return name.substring(0, 1).toUpperCase(displayLocale) + name.substring(1);
    }

    private static String defaultCountryForLanguage(String lang) {
        switch (lang) {
            case "de":
                return "DE";
            case "fr":
                return "FR";
            case "es":
                return "ES";
            case "it":
                return "IT";
            case "nl":
                return "NL";
            case "pt":
                return "PT";
            case "pl":
                return "PL";
            case "sv":
                return "SE";
            case "tr":
                return "TR";
            case "ru":
                return "RU";
            case "ja":
                return "JP";
            case "ar":
                return "SA";
            case "zh":
                return "CN";
            default:
                return "US";
        }
    }
}
