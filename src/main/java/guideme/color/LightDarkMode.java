package guideme.color;

import guideme.internal.GuideME;

public enum LightDarkMode {
    LIGHT_MODE,
    DARK_MODE;

    public static LightDarkMode current() {
        return GuideME.currentLightDarkMode();
    }
}
