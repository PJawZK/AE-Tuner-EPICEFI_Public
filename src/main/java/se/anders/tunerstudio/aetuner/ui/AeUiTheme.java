package se.anders.tunerstudio.aetuner.ui;

import java.awt.Color;

/** Shared two-palette production theme authority ported verbatim from v0.19. */
public final class AeUiTheme {
    public enum Side { LIGHT_SIDE, DARK_SIDE }

    private static volatile Side side = Side.LIGHT_SIDE;
    private AeUiTheme() { }

    public static Side side() { return side; }
    public static boolean isDark() { return side == Side.DARK_SIDE; }
    public static void set(Side next) { side = next == null ? Side.LIGHT_SIDE : next; }
    public static void toggle() { set(isDark() ? Side.LIGHT_SIDE : Side.DARK_SIDE); }
    public static String displayName() { return isDark() ? "Dark Side" : "Light Side"; }

    public static Color background() { return isDark() ? new Color(24,28,34) : new Color(242,244,247); }
    public static Color focusBackground() { return isDark() ? new Color(24,29,36) : new Color(242,246,250); }
    public static Color card() { return isDark() ? new Color(34,40,48) : Color.WHITE; }
    public static Color panel() { return isDark() ? new Color(29,34,41) : new Color(235,238,243); }
    public static Color border() { return isDark() ? new Color(69,78,90) : new Color(207,213,221); }
    public static Color text() { return isDark() ? new Color(232,237,243) : new Color(28,33,40); }
    public static Color focusText() { return isDark() ? new Color(232,238,246) : new Color(23,38,60); }
    public static Color muted() { return isDark() ? new Color(160,172,187) : new Color(96,105,118); }
    public static Color focusMuted() { return isDark() ? new Color(155,173,195) : new Color(78,100,128); }
    public static Color navy() { return isDark() ? new Color(136,181,225) : new Color(35,72,108); }
    public static Color blue() { return isDark() ? new Color(88,157,255) : new Color(42,108,214); }
    public static Color focusBlue() { return isDark() ? new Color(78,153,255) : new Color(24,101,220); }
    public static Color blue2() { return isDark() ? new Color(109,176,255) : new Color(69,145,239); }
    public static Color green() { return isDark() ? new Color(69,190,108) : new Color(41,157,84); }
    public static Color focusGreen() { return isDark() ? new Color(70,194,108) : new Color(30,164,81); }
    public static Color amber() { return isDark() ? new Color(255,197,67) : new Color(255,197,54); }
    public static Color focusAmber() { return isDark() ? new Color(246,172,50) : new Color(229,143,0); }
    public static Color amberDark() { return isDark() ? new Color(176,119,18) : new Color(168,111,0); }
    public static Color red() { return isDark() ? new Color(241,103,96) : new Color(213,66,58); }
    public static Color purple() { return isDark() ? new Color(157,137,255) : new Color(98,75,200); }
    public static Color softBlue() { return isDark() ? new Color(31,49,72) : new Color(229,237,252); }
    public static Color focusSoftBlue() { return isDark() ? new Color(31,51,78) : new Color(235,244,255); }
    public static Color softGreen() { return isDark() ? new Color(31,59,43) : new Color(226,246,233); }
    public static Color focusSoftGreen() { return isDark() ? new Color(31,62,44) : new Color(232,249,237); }
    public static Color softAmber() { return isDark() ? new Color(70,55,28) : new Color(255,246,221); }
    public static Color softRed() { return isDark() ? new Color(72,38,38) : new Color(255,235,232); }
    public static Color softPurple() { return isDark() ? new Color(49,42,76) : new Color(239,235,255); }
    public static Color disabled() { return isDark() ? new Color(27,32,39) : new Color(229,232,236); }

    public static Color taskAvailableBg() { return isDark() ? new Color(45,54,66) : card(); }
    public static Color taskAvailableText() { return isDark() ? new Color(238,242,247) : text(); }
    public static Color taskStatusText() { return isDark() ? new Color(184,197,212) : muted(); }
    public static Color taskAvailableBorder() { return isDark() ? new Color(78,91,107) : border(); }
    public static Color taskDisabledBg() { return isDark() ? new Color(18,23,29) : disabled(); }
    public static Color taskDisabledText() { return isDark() ? new Color(74,90,107) : new Color(184,207,229); }
    public static Color taskDisabledStatusText() { return isDark() ? new Color(57,70,84) : new Color(176,201,223); }
    public static Color taskDisabledBorder() { return isDark() ? new Color(34,41,49) : border(); }
    public static Color taskSelectedBg() { return isDark() ? new Color(31,64,96) : new Color(178,207,232); }
    public static Color taskSelectedText() { return isDark() ? Color.WHITE : new Color(22,39,58); }
    public static Color taskSelectedStatusText() { return isDark() ? new Color(216,232,247) : new Color(58,82,105); }
    public static Color taskSelectedBorder() { return isDark() ? new Color(86,149,211) : new Color(121,159,191); }
    public static Color taskGroupOff() { return isDark() ? new Color(126,142,158) : muted(); }

    public static Color button() { return isDark() ? new Color(45,52,62) : new Color(238,240,243); }
    public static Color buttonAlt() { return isDark() ? new Color(43,51,62) : new Color(242,245,249); }
    public static Color buttonBorder() { return isDark() ? new Color(78,90,105) : new Color(187,193,202); }
    public static Color focusButtonBorder() { return isDark() ? new Color(82,101,122) : new Color(171,187,204); }
    public static Color chartGrid() { return isDark() ? new Color(58,67,79) : new Color(226,233,241); }
    public static Color chartGridSoft() { return isDark() ? new Color(53,61,72) : new Color(232,237,243); }
    public static Color tableHeader() { return isDark() ? new Color(43,50,59) : new Color(238,241,245); }
    public static Color neutralSoft() { return isDark() ? new Color(39,45,54) : new Color(246,248,251); }
    public static Color selection() { return isDark() ? new Color(103,165,255) : new Color(16,71,160); }
    public static Color inactiveStep() { return isDark() ? new Color(59,66,76) : new Color(228,231,236); }
    public static Color mapGrid() { return isDark() ? new Color(73,86,101) : new Color(174,190,207); }
    public static Color legendBorder() { return isDark() ? new Color(86,99,114) : new Color(145,155,168); }
    public static Color thresholdFill() { return isDark() ? new Color(45,63,84) : new Color(215,229,246); }
    public static Color clusterFill() { return isDark() ? new Color(39,59,82) : new Color(218,234,252); }
    public static Color overlay() { return isDark() ? new Color(130,145,164,55) : new Color(190,200,212,70); }
    public static Color referenceLine() { return isDark() ? new Color(156,170,190) : new Color(130,145,164); }
    public static Color noiseLine() { return isDark() ? new Color(132,173,222) : new Color(150,180,220); }
    public static Color milestone() { return isDark() ? new Color(132,172,220) : new Color(65,100,145); }
}
