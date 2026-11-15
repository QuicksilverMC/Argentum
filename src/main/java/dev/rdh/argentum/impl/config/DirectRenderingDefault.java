package dev.rdh.argentum.impl.config;

import java.util.regex.Pattern;

public final class DirectRenderingDefault {
    private static final Pattern LOW_BANDWIDTH = Pattern.compile(String.join("|",
            "intel(?!.*(arc\\W*(tm\\W*)?(pro )?(a\\d{2,3}|b[5-9]\\d{1,2})|\\((dg2|bmg)\\b))",
            "radeon(?!.*\\brx ?\\d).*graphics(?!.*\\b(llvm|drm)\\b)",
            "\\b(raven2?|picasso|renoir|cezanne|lucienne|barcelo|vangogh|rembrandt|raphael|mendocino|phoenix|strix|krackan"
                    + "|carrizo|stoney|kaveri|kabini|mullins|gfx90[29c]|gfx103[3567]|gfx1103|gfx115\\d)",
            "radeon\\W*(tm\\W*)?\\d{3}m\\b", "rx ?5[56]0\\b", "rx ?6[45]00\\b",
            "\\bgt \\d", "geforce \\d{3}m\\b", "\\bmx ?\\d", "\\d{3}mx\\b", "gtx (\\d{3}m?\\b|1050|1630|1650)"
    ), Pattern.CASE_INSENSITIVE);

    private static boolean enabled;

    private DirectRenderingDefault() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void detect(String renderer) {
        enabled = !System.getProperty("os.name").startsWith("Mac") && isLowBandwidth(renderer);
    }

    static boolean isLowBandwidth(String renderer) {
        return renderer != null && LOW_BANDWIDTH.matcher(renderer).find();
    }
}
