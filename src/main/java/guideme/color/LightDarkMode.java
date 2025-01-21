package guideme.color;

import guideme.internal.GuideMEClient;

public enum LightDarkMode {
    LIGHT_MODE,
    DARK_MODE;

    public static LightDarkMode current() {
        return GuideMEClient.currentLightDarkMode();
    }
}
