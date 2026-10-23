package addon;

import com.charlesproxy.CharlesContext;
import com.charlesproxy.ssl.CertificateStore;
import com.charlesproxy.ssl.rkLW;
import java.awt.Component;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class Hooks {
    private static String rememberedAlias;

    private static Path aliasFile() {
        return Path.of(System.getProperty("charles.pem.profile", com.charlesproxy.JovI.yPDK()))
            .resolve("imported-ca-alias.txt");
    }

    private static String importedAlias() {
        if (rememberedAlias == null) {
            try { rememberedAlias = java.nio.file.Files.exists(aliasFile()) ? java.nio.file.Files.readString(aliasFile()).trim() : ""; }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        return rememberedAlias;
    }

    private static void rememberAlias(String alias) throws java.io.IOException {
        java.nio.file.Files.writeString(aliasFile(), alias);
        rememberedAlias = alias;
    }
    private static volatile PrivateKey signingKey;
    private static volatile X509Certificate[] signingChain;

    public static void install(JMenu help) {
        JMenu ssl = null;
        for (Component item : help.getMenuComponents()) {
            if (item instanceof JMenu menu) { ssl = menu; break; }
        }
        if (ssl == null) throw new IllegalStateException("SSL Proxying menu not found");
        JMenu source = new JMenu("Signing Certificate");
        JRadioButtonMenuItem original = new JRadioButtonMenuItem("Original Charles Certificate");
        JRadioButtonMenuItem imported = new JRadioButtonMenuItem("Imported Root / Intermediate Certificate");
        ButtonGroup group = new ButtonGroup();
        group.add(original); group.add(imported);
        source.add(original); source.add(imported);
        original.addActionListener(event -> attempt(help, () -> select(false)));
        imported.addActionListener(event -> attempt(help, () -> select(true)));
        source.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent event) {
                boolean custom = context().getConfiguration().yPDK().iIEW() != null;
                original.setSelected(!custom); imported.setSelected(custom);
                imported.setEnabled(custom || !importedAlias().isEmpty());
            }
            public void menuDeselected(MenuEvent event) { }
            public void menuCanceled(MenuEvent event) { }
        });
        ssl.addSeparator();
        ssl.add(source);
        JMenuItem load = new JMenuItem("Import Root/Intermediate Certificate...");
        load.addActionListener(event -> attempt(help, () -> importPem(help)));
        ssl.add(load);
    }

    private static CharlesContext context() { return CharlesContext.getInstance(); }

    private static boolean unlock() {
        CertificateStore store = context().getSslManager().Yctd();
        if (store.yPDK().ordinal() == 0 && !store.xGrV()) return false;
        store.Oezn();
        return store.yPDK().ordinal() == 2;
    }

    private static void select(boolean imported) throws Exception {
        var config = context().getConfiguration().yPDK();
        rkLW current = config.iIEW();
        if (current != null && current.ScVR() != null) rememberAlias(current.ScVR());
        if (!imported) {
            config.yPDK((rkLW)null);
        } else {
            if (!unlock()) return;
            var entry = context().getSslManager().Yctd().Oezn(importedAlias());
            if (entry == null) throw new IllegalArgumentException("Import a CA certificate and PEM private key first.");
            ((X509Certificate)entry.Oezn()).checkValidity();
            config.yPDK(new rkLW(entry));
        }
        context().saveConfig();
    }

    private static Path choose(Component parent, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("PEM files", "pem", "crt", "cer", "key"));
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private static void importPem(Component parent) throws Exception {
        Path certificates = choose(parent, "Choose CA / intermediate certificate PEM (may contain chain)");
        if (certificates == null) return;
        Path key = choose(parent, "Choose matching private key PEM");
        if (key == null) return;
        PemIdentity identity;
        try {
            identity = PemIdentity.read(certificates, key, null);
        } catch (PemIdentity.PasswordRequired required) {
            JPasswordField password = new JPasswordField(25);
            if (JOptionPane.showConfirmDialog(parent, password, "PEM private key password",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            char[] secret = password.getPassword();
            password.setText("");
            try { identity = PemIdentity.read(certificates, key, secret); }
            finally { Arrays.fill(secret, '\0'); }
        }
        if (!unlock()) return;
        var entry = new com.xk72.njup.rkLW(identity.chain()[0].getSubjectX500Principal().getName(),
            identity.chain()[0], identity.key(), identity.chain());
        entry = context().getSslManager().Yctd().Oezn(entry);
        rememberAlias(entry.yPDK());
        context().getConfiguration().yPDK().yPDK(new rkLW(entry));
        context().saveConfig();
        JOptionPane.showMessageDialog(parent, "Imported and selected:\n" + identity.chain()[0].getSubjectX500Principal()
            + "\nNew TLS connections will use this CA. Reconnect existing clients.", "Import Certificate", JOptionPane.INFORMATION_MESSAGE);
    }

    // Invoked whenever Charles installs either its own CA or a custom CA.
    public static void caChanged(X509Certificate certificate, PrivateKey key) {
        signingKey = key;
        signingChain = new X509Certificate[]{certificate};
        var ctx = context();
        if (ctx != null && ctx.getSslManager() != null) {
            var selected = ctx.getConfiguration().yPDK().iIEW();
            if (selected != null && selected.ScVR() != null) {
                var entry = ctx.getSslManager().Yctd().Oezn(selected.ScVR());
                if (entry != null && certificate.equals(entry.Oezn()))
                    signingChain = Arrays.copyOf(entry.ScVR(), entry.ScVR().length, X509Certificate[].class);
            }
        }
        try {
            var field = Class.forName("com.xk72.proxy.ssl.eZEl").getDeclaredField("jwHf");
            field.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>)field.get(null);
            synchronized (cache) { cache.clear(); }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static String signatureAlgorithm() { return PemIdentity.algorithm(signingKey); }

    public static java.security.cert.Certificate[] completeChain(java.security.cert.Certificate[] original) {
        X509Certificate[] chain = signingChain;
        // During SSLManager construction the context has not published the manager yet.
        // Resolve again here so startup and restored sessions get the complete chain.
        var ctx = context();
        if (chain != null && ctx != null && ctx.getSslManager() != null) {
            var selected = ctx.getConfiguration().yPDK().iIEW();
            var store = ctx.getSslManager().Yctd();
            if (selected != null && selected.ScVR() != null && store.yPDK().ordinal() == 2) {
                var entry = store.Oezn(selected.ScVR());
                if (entry != null && chain[0].equals(entry.Oezn())) {
                    chain = Arrays.copyOf(entry.ScVR(), entry.ScVR().length, X509Certificate[].class);
                    signingChain = chain;
                }
            }
        }
        if (chain == null || original.length != 2 || !original[1].equals(chain[0])) return original;
        java.security.cert.Certificate[] result = new java.security.cert.Certificate[chain.length + 1];
        result[0] = original[0];
        System.arraycopy(chain, 0, result, 1, chain.length);
        return result;
    }

    public static void initKeyManager(javax.net.ssl.KeyManagerFactory factory, KeyStore store, char[] password)
            throws GeneralSecurityException {
        if (store.isKeyEntry("mycert")) {
            var original = store.getCertificateChain("mycert");
            var completed = completeChain(original);
            if (completed != original) store.setKeyEntry("mycert", store.getKey("mycert", password), password, completed);
        }
        factory.init(store, password);
    }

    private interface Task { void run() throws Exception; }
    private static void attempt(Component parent, Task task) {
        try { task.run(); }
        catch (Exception error) {
            error.printStackTrace();
            JOptionPane.showMessageDialog(parent, error.getMessage(), "Certificate operation failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
