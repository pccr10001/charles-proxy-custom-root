import addon.*;
import com.charlesproxy.CharlesContext;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import javax.swing.*;

public class Integration extends CharlesContext {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("charles-addon-integration-");
        System.setProperty("charles.data", temp.toString());
        System.setProperty("charles.config", temp.resolve("charles.config").toString());
        Integration context = new Integration();
        try {
            context.init(); // Initialise settings and SSL only; do not start proxy listeners or change system proxies.
            var manager = context.getSslManager();
            X509Certificate original = manager.ZJTz();
            var store = manager.Yctd();
            store.yPDK("test-password".toCharArray());
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", "BC"); gen.initialize(2048);
            KeyPair rootKey = gen.generateKeyPair(), intermediateKey = gen.generateKeyPair();
            X509Certificate root = Verify.cert("Integration Root", rootKey, null, null, true);
            X509Certificate intermediate = Verify.cert("Integration Intermediate", intermediateKey, root, rootKey, true);
            var entry = store.Oezn(new com.xk72.njup.rkLW("test", intermediate, intermediateKey.getPrivate(),
                new java.security.cert.Certificate[]{intermediate, root}));
            context.getConfiguration().yPDK().yPDK(new com.charlesproxy.ssl.rkLW(entry));
            Verify.check(manager.ZJTz().equals(intermediate), "native custom root configuration");
            context.saveConfig();
            Verify.check(Files.size(temp.resolve("charles.config")) > 0, "configuration saved");
            try (var reader = Files.newBufferedReader(temp.resolve("charles.config"))) {
                var restored = (com.charlesproxy.config.fgXo)new com.xk72.serialization.Wahx().yPDK(reader);
                Verify.check(restored.yPDK().iIEW().ScVR().equals(entry.yPDK()), "imported CA restored from XML");
            }
            var received = Verify.handshake(com.xk72.proxy.ssl.eZEl.yPDK("native-store.test"));
            Verify.check(received.length == 3, "chain obtained from native secure store");
            ((X509Certificate)received[0]).verify(intermediate.getPublicKey());
            context.getConfiguration().yPDK().yPDK((com.charlesproxy.ssl.rkLW)null);
            Verify.check(manager.ZJTz().equals(original), "original CA restored without regeneration");
            context.saveConfig();
            try (var reader = Files.newBufferedReader(temp.resolve("charles.config"))) {
                var restored = (com.charlesproxy.config.fgXo)new com.xk72.serialization.Wahx().yPDK(reader);
                Verify.check(restored.yPDK().iIEW() == null, "original CA selection persisted");
            }
            received = Verify.handshake(com.xk72.proxy.ssl.eZEl.yPDK("native-store.test"));
            ((X509Certificate)received[0]).verify(original.getPublicKey());
            SwingUtilities.invokeAndWait(() -> {
                JMenu help = new JMenu("Help"), ssl = new JMenu("SSL Proxying"); help.add(ssl);
                Hooks.install(help);
                Verify.check(ssl.getItem(1) instanceof JMenu, "signing certificate menu installed");
                Verify.check(ssl.getItem(2).getText().equals("Import Root/Intermediate Certificate..."), "PEM import menu installed");
            });
            Class.forName("com.charlesproxy.gui.menus.HelpMenu");
            System.out.println("PASS native secure store, configuration switching, original CA preserved, menu hooks, bytecode verification");
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
    @Override public void error(String title, String message, Throwable cause) {
        throw new IllegalStateException(title + ": " + message, cause);
    }
}
