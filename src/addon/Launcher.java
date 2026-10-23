package addon;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class Launcher {
    public static void main(String[] args) throws Exception {
        try {
            verifyDistribution();
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            Path home = Path.of(System.getProperty("user.home"));
            Path profile, originalConfig, originalData;
            if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                Path base = appData == null ? home : Path.of(appData);
                profile = base.resolve("Charles-PEM");
                originalConfig = base.resolve("Charles/charles.config");
                originalData = base.resolve("Charles/data");
            } else if (os.contains("mac")) {
                Path support = home.resolve("Library/Application Support");
                profile = support.resolve("Charles-PEM");
                originalConfig = home.resolve("Library/Preferences/com.xk72.charles.config");
                originalData = support.resolve("Charles");
            } else {
                profile = home.resolve(".charles-pem");
                originalConfig = home.resolve(".charles.config");
                originalData = home.resolve(".charles");
            }
            configureProfile(profile, originalConfig, originalData);
            com.charlesproxy.main.Main.main(args);
        } catch (Exception failure) {
            failure.printStackTrace();
            if (!java.awt.GraphicsEnvironment.isHeadless())
                javax.swing.JOptionPane.showMessageDialog(null, failure.toString(), "Charles PEM add-on", javax.swing.JOptionPane.ERROR_MESSAGE);
            throw failure;
        }
    }

    private static void verifyDistribution() throws Exception {
        Path jar = Path.of(System.getProperty("charles.pem.home", "."), "lib", "charles.jar");
        String actual;
        try (var input = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            actual = HexFormat.of().formatHex(digest.digest());
        }
        if (!actual.equals("00f5f1073e1c104587cf079ad58e1bf25bd65ef8997c70ab97a73a4fc6d36eb8"))
            throw new IOException("Unsupported Charles build. This add-on requires the tested Charles 5.2.2 JAR; see README.md.");
    }

    public static void configureProfile(Path profile, Path original) throws IOException {
        configureProfile(profile, original.resolve("charles.config"), original.resolve("data"));
    }

    public static void configureProfile(Path profile, Path originalConfig, Path originalData) throws IOException {
        Path config = profile.resolve("charles.config");
        Path data = profile.resolve("data");
        if (!Files.exists(profile)) {
            Path staging = Files.createTempDirectory(profile.toAbsolutePath().getParent(), "Charles-PEM-init-");
            copyTree(originalData, staging.resolve("data"));
            Files.createDirectories(staging.resolve("data"));
            if (Files.isRegularFile(originalConfig))
                Files.copy(originalConfig, staging.resolve("charles.config"));
            Files.move(staging, profile);
        }
        Files.createDirectories(data);
        System.setProperty("charles.config", config.toAbsolutePath().toString());
        System.setProperty("charles.data", data.toAbsolutePath().toString());
        // Keep the remembered imported CA alias inside this profile as well.
        System.setProperty("charles.pem.profile", profile.toAbsolutePath().toString());
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) continue;
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path)) Files.copy(path, destination);
            }
        }
    }
}
