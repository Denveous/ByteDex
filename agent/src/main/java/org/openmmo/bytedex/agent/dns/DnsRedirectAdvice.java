package org.openmmo.bytedex.agent.dns;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.net.InetAddress;

public final class DnsRedirectAdvice {

    public static final String SYSTEM_PROPERTY = "bytedex.dnsRedirects";

    private DnsRedirectAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Object enter(
        @Advice.Argument(0) String host,
        @Advice.Origin("#m") String methodName
    ) {
        if (host == null) return null;
        String list = System.getProperty(SYSTEM_PROPERTY);
        if (list == null || list.isEmpty()) return null;

        int start = 0;
        boolean matched = false;
        int len = list.length();
        while (start <= len) {
            int end = list.indexOf(',', start);
            if (end < 0) end = len;
            int segLen = end - start;
            if (segLen == host.length()
                && list.regionMatches(true, start, host, 0, segLen)) {
                matched = true;
                break;
            }
            start = end + 1;
        }
        if (!matched) return null;

        try {
            InetAddress addr = InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1});
            if ("getAllByName".equals(methodName)) {
                return new InetAddress[]{addr};
            }
            return addr;
        } catch (Exception e) {
            return null;
        }
    }

    @Advice.OnMethodExit
    @SuppressWarnings({"java:S1226", "java:S1854"})
    public static void exit(
        @Advice.Enter Object entered,
        @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned
    ) {
        if (entered != null) {
            returned = entered;
        }
    }
}
