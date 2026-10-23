import java.nio.file.*;
import javax.swing.*;

public class MenuSmoke {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("charles-addon-menu-");
        System.setProperty("charles.data", temp.toString());
        System.setProperty("charles.config", temp.resolve("charles.config").toString());
        try {
            var context = new com.charlesproxy.gui.ATmO() {
                @Override public void trace(String message) { }
                @Override public void error(String title, String message, Throwable cause) {
                    throw new IllegalStateException(title + ": " + message, cause);
                }
            };
            context.init();
            SwingUtilities.invokeAndWait(() -> {
                var frame = new com.charlesproxy.gui.CharlesFrame(context);
                try {
                    var help = new com.charlesproxy.gui.menus.HelpMenu("Help", frame);
                    JMenu ssl = null;
                    for (var item : help.getMenuComponents()) if (item instanceof JMenu menu) { ssl = menu; break; }
                    boolean source = false, imported = false;
                    for (var item : ssl.getMenuComponents()) {
                        if (item instanceof JMenuItem menu) {
                            source |= menu.getText().equals("Signing Certificate");
                            imported |= menu.getText().equals("Import Root/Intermediate Certificate...");
                        }
                    }
                    if (!source || !imported) throw new AssertionError("Missing added menus");
                    System.out.println("PASS real Charles HelpMenu constructor and SSL Proxying menu entries");
                } finally { frame.dispose(); }
            });
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
}
