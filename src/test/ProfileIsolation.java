import addon.Launcher;
import java.nio.file.*;

public class ProfileIsolation {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("charles-profile-isolation-");
        Path original = temp.resolve("Charles"), profile = temp.resolve("Charles-PEM");
        Files.createDirectories(original.resolve("data/ca"));
        Files.writeString(original.resolve("charles.config"), "original config");
        Files.writeString(original.resolve("data/ca/keystore"), "original CA");
        Launcher.configureProfile(profile, original);
        if (!System.getProperty("charles.config").equals(profile.resolve("charles.config").toString())) throw new AssertionError("config path");
        if (!System.getProperty("charles.data").equals(profile.resolve("data").toString())) throw new AssertionError("data path");
        if (!Files.readString(profile.resolve("data/ca/keystore")).equals("original CA")) throw new AssertionError("CA copied");
        Files.writeString(profile.resolve("charles.config"), "addon config");
        Files.writeString(profile.resolve("data/ca/keystore"), "addon CA");
        Launcher.configureProfile(profile, original);
        if (!Files.readString(original.resolve("charles.config")).equals("original config")) throw new AssertionError("original config changed");
        if (!Files.readString(original.resolve("data/ca/keystore")).equals("original CA")) throw new AssertionError("original CA changed");
        if (!Files.readString(profile.resolve("charles.config")).equals("addon config")) throw new AssertionError("addon config overwritten");
        System.out.println("PASS independent config/data, initial CA copy, original profile preserved, addon changes retained");
    }
}
