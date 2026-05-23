package org.openmmo.bytedex.agent.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyGen {

    private KeyGen() {}

    public static void main(String[] args) throws Exception {
        Path originalsPath = null;
        Path outPath = null;
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            switch (arg) {
                case "--originals": originalsPath = Path.of(args[i++]); break;
                case "--out": outPath = Path.of(args[i++]); break;
                default:
                    System.err.println("unknown arg: " + arg);
                    System.exit(2);
            }
        }
        if (originalsPath == null || outPath == null) {
            System.err.println("usage: KeyGen --originals <path> --out <path>");
            System.exit(2);
        }

        if (Files.exists(outPath)) {
            System.out.println("keys already exist at " + outPath + " - skipping (clean to regenerate)");
            return;
        }

        JsonObject originals;
        try (BufferedReader r = Files.newBufferedReader(originalsPath)) {
            originals = JsonParser.parseReader(r).getAsJsonObject();
        }

        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());
        Base64.Encoder b64 = Base64.getEncoder();

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : originals.entrySet()) {
            String name = entry.getKey();
            String original = entry.getValue().getAsString();

            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = gen.generateKeyPair();
            String newPub = b64.encodeToString(kp.getPublic().getEncoded());
            String newPriv = b64.encodeToString(kp.getPrivate().getEncoded());

            Map<String, String> data = new LinkedHashMap<>();
            data.put("originalPublicKey", original);
            data.put("newPublicKey", newPub);
            data.put("newPrivateKey", newPriv);
            result.put(name, data);

            System.out.println(name + " replacement public key = " + newPub);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (BufferedWriter w = Files.newBufferedWriter(outPath)) {
            gson.toJson(result, w);
            w.write("\n");
        }
        System.out.println("wrote " + outPath.toAbsolutePath());
    }
}
