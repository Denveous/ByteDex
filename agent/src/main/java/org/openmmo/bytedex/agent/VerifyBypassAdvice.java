package org.openmmo.bytedex.agent;

import net.bytebuddy.asm.Advice;

public final class VerifyBypassAdvice {

    private VerifyBypassAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static int enter() {
        return 1;
    }

    @Advice.OnMethodExit
    @SuppressWarnings({"unused", "java:S1226"})
    public static void exit(@Advice.Return(readOnly = false) boolean returned) {
        returned = true;
    }
}
