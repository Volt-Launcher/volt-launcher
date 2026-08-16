package app.voltlauncher.setup.shortcut;

import app.voltlauncher.setup.SetupOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Creates Windows {@code .lnk} shortcuts.
 *
 * <p>The shortcut format is an OLE structured-storage document that the JDK cannot write, so the
 * work is delegated to the {@code WScript.Shell} COM object through PowerShell — the same
 * mechanism Windows installers use, and available on every supported Windows version.
 */
public final class WindowsShortcutInstaller implements ShortcutInstaller {

    private static final long SCRIPT_TIMEOUT_SECONDS = 30;

    @Override
    public String createApplicationEntry(Path executable, Path iconFile) throws Exception {
        Path startMenu = Path.of(System.getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs");
        Path shortcut = startMenu.resolve(SetupOptions.APP_NAME + ".lnk");
        writeShortcut(shortcut, executable, iconFile);
        return "Start Menu entry: " + shortcut;
    }

    @Override
    public String createDesktopShortcut(Path executable, Path iconFile) throws Exception {
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Path shortcut = desktop.resolve(SetupOptions.APP_NAME + ".lnk");
        writeShortcut(shortcut, executable, iconFile);
        return "Desktop shortcut: " + shortcut;
    }

    private void writeShortcut(Path shortcut, Path executable, Path iconFile) throws Exception {
        Files.createDirectories(shortcut.getParent());

        String script = """
                $shell = New-Object -ComObject WScript.Shell
                $link = $shell.CreateShortcut(%s)
                $link.TargetPath = %s
                $link.WorkingDirectory = %s
                $link.Description = 'A modern Minecraft launcher'
                %s
                $link.Save()
                """.formatted(
                quote(shortcut.toString()),
                quote(executable.toString()),
                quote(executable.getParent().toString()),
                iconFile != null && Files.exists(iconFile)
                        ? "$link.IconLocation = " + quote(iconFile.toString())
                        : "");

        Path scriptFile = Files.createTempFile("volt-shortcut-", ".ps1");
        try {
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
            runPowerShell(scriptFile);
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    private void runPowerShell(Path scriptFile) throws Exception {
        List<String> command = List.of(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", scriptFile.toString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Creating the shortcut timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Creating the shortcut failed: " + output.trim());
        }
    }

    /** PowerShell single-quoted string: the only escape needed is doubling an embedded quote. */
    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
