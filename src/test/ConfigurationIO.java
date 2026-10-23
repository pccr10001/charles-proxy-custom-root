import java.io.*;
import java.nio.file.*;
import com.xk72.serialization.Wahx;

public class ConfigurationIO {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        byte[] original = Files.readAllBytes(file);
        Object config;
        try (var reader = Files.newBufferedReader(file)) { config = new Wahx().yPDK(reader); }
        if (!config.getClass().getName().equals("com.charlesproxy.config.fgXo")) throw new AssertionError("Configuration type");
        StringWriter writer = new StringWriter();
        new Wahx().yPDK(config, writer);
        Object restored = new Wahx().yPDK(new StringReader(writer.toString()));
        if (!restored.getClass().equals(config.getClass())) throw new AssertionError("Roundtrip type");
        if (!java.util.Arrays.equals(original, Files.readAllBytes(file))) throw new AssertionError("Original file modified");
        System.out.println("PASS existing configuration read, in-memory serialization roundtrip, source file unchanged");
    }
}
