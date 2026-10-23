# Charles Proxy Custom Root

Select the original Charles CA or an imported root/intermediate CA for SSL
Proxying, without modifying `lib/charles.jar` or `Charles.ini`.

## Compatibility

Version **v0.1** targets the tested **Charles Proxy 5.2.2** distribution and
requires **Java 25 or later**. The original JAR must have SHA-256:

```text
00F5F1073E1C104587CF079AD58E1BF25BD65EF8997C70AB97A73A4FC6D36EB8
```

The launcher checks this fingerprint before loading settings. Other builds,
including other JARs labeled 5.2.2, require adapting and testing the overlays.
Windows is tested. The shell launcher and macOS/Linux profile paths are
provided, but those platforms have not been tested; their JAR must also match.

## Install and launch

1. Download this repository's source ZIP and extract it, or clone the repository.
2. Download **charles-pem-addon.jar** from
   [release v0.1](https://github.com/pccr10001/charles-proxy-custom-root/releases/tag/v0.1).
   Place it beside `launch.bat`, `launch.ps1`, and `launch.sh`.
3. Install Java 25+ and set `JAVA_HOME` to its installation directory.
   Charles's bundled runtime can be used if it includes the appropriate Java
   executable. Some Charles distributions omit `java.exe`/`javaw.exe` and need
   a separate JDK/runtime.
4. Close Charles before the first launch so its profile can be copied consistently.

### Windows

Place this directory directly inside the Charles installation directory, or
set `CHARLES_HOME` to the directory containing `lib/charles.jar`. The launcher
also checks its own directory and `%ProgramFiles%\Charles`.

Double-click **launch.bat**. It starts a hidden PowerShell helper, starts
`javaw.exe`, and exits. Only the Charles UI remains. Startup errors appear in
a dialog. Logs are written to `%APPDATA%\Charles-PEM-Logs`.

You can also launch directly from PowerShell:

```powershell
.\launch.ps1 -CharlesHome 'C:\Program Files\Charles' -JavaHome 'C:\Java\jdk-25'
```

The PS1 returns immediately after starting Java. It does not keep a helper
console open or close an existing interactive terminal that you launched it from.
Optional Charles arguments can be supplied with `-CharlesArgs @('--debug')`.

### Linux / macOS

```sh
chmod +x launch.sh
CHARLES_HOME=/opt/charles JAVA_HOME=/path/to/jdk-25 ./launch.sh
```

`CHARLES_HOME` must contain `lib/charles.jar`; adjust it to your installation's
layout. The process runs in the background with detached standard input. Logs
are written to `${XDG_STATE_HOME:-$HOME/.local/state}/charles-pem` on Linux or
`~/Library/Logs/Charles-PEM` on macOS. Arguments are forwarded, for example
`./launch.sh --debug`.

## Import and switch CA

In **Help > SSL Proxying**:

- **Import Root/Intermediate Certificate...** selects the certificate PEM,
  then its matching private key PEM. A successful import selects that CA.
- **Signing Certificate > Original Charles Certificate** restores the
  original automatically generated CA.
- **Signing Certificate > Imported Root / Intermediate Certificate** switches
  back to the most recently selected imported CA.

Supports RSA PKCS#1, EC SEC1, and PKCS#8 PEM private keys, including encrypted
PEM. RSA and EC signing are supported. The importer checks validity, CA basic
constraints, signing usage, key matching, and any supplied certificate chain.
The certificate PEM may contain the intermediate chain in either order.

Unlock or create the Charles Secure Store when prompted. Private keys are
saved in its password-protected storage; source PEM files are not modified.
New TLS connections use the selected CA. Reconnect existing clients after
switching. Clients still need to trust the chosen CA chain; importing here
does not install a trusted root into the OS or bypass client certificate checks.

## Independent settings

| Platform | Add-on profile |
| --- | --- |
| Windows | `%APPDATA%\Charles-PEM` |
| macOS | `~/Library/Application Support/Charles-PEM` |
| Linux | `~/.charles-pem` |

Each profile contains `charles.config`, `data/`, and, after selecting an
imported CA, `imported-ca-alias.txt`. On first launch, the original configuration
and data are copied into the new profile, preserving the existing Charles CA.
Subsequent changes are saved only to the add-on profile. The original Charles
launcher continues using its original settings and CA store. Explicit Charles
`--config`/`--data` arguments override the default paths.

## Compile from source

Build requirements: JDK 25+, the matching installed Charles distribution,
and **ASM 9.9**. Bouncy Castle and other runtime dependencies come from
Charles's existing `lib/` directory. No build script is required.

### PowerShell

Run from the repository root with the JDK's `bin` directory on `PATH`:

```powershell
$CharlesHome = 'C:\Program Files\Charles'
if ((Get-FileHash "$CharlesHome/lib/charles.jar" -Algorithm SHA256).Hash -ne '00F5F1073E1C104587CF079AD58E1BF25BD65EF8997C70AB97A73A4FC6D36EB8') { throw 'Unsupported Charles JAR' }
$BuildRoot = Join-Path $env:TEMP ('charles-pem-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $BuildRoot 'classes'
$transformer = Join-Path $BuildRoot 'transformer'
New-Item -ItemType Directory -Force $classes, $transformer | Out-Null
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar' -OutFile "$BuildRoot/asm.jar"
javac -cp "$CharlesHome/lib/*" -d $classes src/addon/*.java
if ($LASTEXITCODE) { throw 'Source compilation failed' }
javac -cp "$BuildRoot/asm.jar" -d $transformer src/build/Patch.java
if ($LASTEXITCODE) { throw 'Transformer compilation failed' }
java -cp "$transformer;$BuildRoot/asm.jar;$CharlesHome/lib/*" Patch "$CharlesHome/lib/charles.jar" $classes
if ($LASTEXITCODE) { throw 'Overlay generation failed' }
Copy-Item src/resources/* $classes -Recurse -Force
jar --create --file charles-pem-addon.jar -C $classes .
if ($LASTEXITCODE) { throw 'Packaging failed' }
```

### POSIX shell

```sh
set -eu
export CHARLES_HOME=/opt/charles
build_root=$(mktemp -d)
mkdir -p "$build_root/classes" "$build_root/transformer"
curl -fL https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar -o "$build_root/asm.jar"
javac -cp "$CHARLES_HOME/lib/*" -d "$build_root/classes" src/addon/*.java
javac -cp "$build_root/asm.jar" -d "$build_root/transformer" src/build/Patch.java
java -cp "$build_root/transformer:$build_root/asm.jar:$CHARLES_HOME/lib/*" Patch "$CHARLES_HOME/lib/charles.jar" "$build_root/classes"
cp -R src/resources/. "$build_root/classes/"
jar --create --file charles-pem-addon.jar -C "$build_root/classes" .
```

`src/build/Patch.java` is the bytecode transformer. It generates two overlay
classes from the installed Charles JAR: the Help menu and SSL context factory.
The original JAR is opened read-only. The generated overlays add the menus,
RSA/EC signing algorithm selection, intermediate-chain delivery, and CA cache
invalidation. `src/resources/META-INF/services` reproduces Charles's module
service registrations so configuration loading/saving works with `-cp`.

### Tests

Using the PowerShell build variables above:

```powershell
$testClasses = Join-Path $BuildRoot 'tests'
New-Item -ItemType Directory -Force $testClasses | Out-Null
$testCp = "$testClasses;$PWD/charles-pem-addon.jar;$CharlesHome/lib/*"
javac -cp $testCp -d $testClasses src/test/*.java
if ($LASTEXITCODE) { throw 'Test compilation failed' }
foreach ($test in @('Verify', 'Integration', 'MenuSmoke', 'ProfileIsolation')) {
    java '-Xverify:all' "-Djava.library.path=$CharlesHome/lib" '--enable-native-access=ALL-UNNAMED' -cp $testCp $test
    if ($LASTEXITCODE) { throw "$test failed" }
}
```

Tests cover RSA/EC PEM parsing, encrypted keys, real local TLS handshakes,
chain delivery, CA switching, native secure-store integration, configuration
save/reload, independent profile copying, and the actual Charles Help menu.
They use temporary Charles profiles and do not start proxy listeners or change
system proxy settings. `ConfigurationIO` can also verify an existing configuration
by reading it and performing an in-memory serialization roundtrip, without
writing back to that file.
