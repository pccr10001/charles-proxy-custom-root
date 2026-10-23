import addon.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.math.BigInteger;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class Verify {
    static int assertions;
    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        assertions++;
    }
    static X509Certificate cert(String name, KeyPair subject, X509Certificate issuer, KeyPair issuerKey, boolean ca) throws Exception {
        var dn = new X500Principal("CN=" + name);
        var builder = new JcaX509v3CertificateBuilder(issuer == null ? dn : issuer.getSubjectX500Principal(),
            new BigInteger(100, new SecureRandom()), new Date(System.currentTimeMillis()-60000),
            new Date(System.currentTimeMillis()+86400000), dn, subject.getPublic());
        builder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true,
            new KeyUsage(ca ? KeyUsage.keyCertSign : KeyUsage.digitalSignature));
        PrivateKey key = issuerKey == null ? subject.getPrivate() : issuerKey.getPrivate();
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(
            new JcaContentSignerBuilder(PemIdentity.algorithm(key)).setProvider("BC").build(key)));
    }
    static void pem(Path file, Object... objects) throws Exception {
        try (JcaPEMWriter writer = new JcaPEMWriter(Files.newBufferedWriter(file))) {
            for (Object object : objects) writer.writeObject(object);
        }
    }
    static java.security.cert.Certificate[] handshake(SSLContext server) throws Exception {
        var trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String a) { }
            public void checkServerTrusted(X509Certificate[] c, String a) { }
        };
        SSLContext client = SSLContext.getInstance("TLS");
        client.init(null, new TrustManager[]{trust}, null);
        try (SSLServerSocket listener = (SSLServerSocket)server.getServerSocketFactory().createServerSocket(0, 1,
                java.net.InetAddress.getLoopbackAddress())) {
            var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var task = worker.submit(() -> {
                    try (SSLSocket socket = (SSLSocket)listener.accept()) { socket.setSoTimeout(10000); socket.startHandshake(); }
                    return null;
                });
                java.security.cert.Certificate[] chain;
                try (SSLSocket socket = (SSLSocket)client.getSocketFactory().createSocket(java.net.InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
                    socket.setSoTimeout(10000); socket.startHandshake(); chain = socket.getSession().getPeerCertificates();
                }
                task.get(15, java.util.concurrent.TimeUnit.SECONDS);
                return chain;
            } finally { worker.shutdownNow(); }
        }
    }
    public static void main(String[] args) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Path temp = Files.createTempDirectory("charles-pem-test-");
        try {
            for (String algorithm : new String[]{"RSA", "EC"}) {
                KeyPairGenerator gen = KeyPairGenerator.getInstance(algorithm, "BC");
                gen.initialize(algorithm.equals("RSA") ? 2048 : 256);
                KeyPair rootKey = gen.generateKeyPair(), intermediateKey = gen.generateKeyPair();
                X509Certificate root = cert("Root " + algorithm, rootKey, null, null, true);
                X509Certificate intermediate = cert("Intermediate " + algorithm, intermediateKey, root, rootKey, true);
                Path certificates = temp.resolve("ca.pem"), key = temp.resolve("key.pem");
                pem(certificates, root, intermediate); pem(key, intermediateKey.getPrivate());
                PemIdentity identity = PemIdentity.read(certificates, key, null);
                check(identity.chain().length == 2 && identity.chain()[0].equals(intermediate), "chain sorting");
                pem(key, org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(intermediateKey.getPrivate().getEncoded()));
                check(PemIdentity.read(certificates, key, null).key() != null, "PKCS8 PEM private key");
                try (JcaPEMWriter writer = new JcaPEMWriter(Files.newBufferedWriter(key))) {
                    writer.writeObject(intermediateKey.getPrivate(), new org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder("AES-256-CBC")
                        .setProvider("BC").build("secret".toCharArray()));
                }
                try { PemIdentity.read(certificates, key, null); throw new AssertionError("no password prompt"); }
                catch (PemIdentity.PasswordRequired expected) { assertions++; }
                check(PemIdentity.read(certificates, key, "secret".toCharArray()).key() != null, "encrypted PEM private key");
                com.xk72.proxy.ssl.eZEl.yPDK(intermediate, identity.key());
                // The production hook obtains the chain from the secure store; isolate TLS testing from user settings.
                var chainField = Hooks.class.getDeclaredField("signingChain"); chainField.setAccessible(true);
                chainField.set(null, identity.chain());
                SSLContext first = com.xk72.proxy.ssl.eZEl.yPDK("pem-addon.test");
                var received = handshake(first);
                check(received.length == 3, "full intermediate chain sent over TLS");
                ((X509Certificate)received[0]).verify(intermediate.getPublicKey());
                check(received[1].equals(intermediate) && received[2].equals(root), "TLS issuer chain");
                com.xk72.proxy.ssl.eZEl.yPDK(root, rootKey.getPrivate());
                SSLContext second = com.xk72.proxy.ssl.eZEl.yPDK("pem-addon.test");
                check(first != second, "CA switch invalidates SSL context cache");
                received = handshake(second);
                ((X509Certificate)received[0]).verify(root.getPublicKey());
                check(received.length == 2 && received[1].equals(root), "CA switch signs with replacement key");
                pem(key, rootKey.getPrivate()); pem(certificates, intermediate);
                try { PemIdentity.read(certificates, key, null); throw new AssertionError("accepted mismatched key"); }
                catch (IllegalArgumentException expected) { assertions++; }
                pem(certificates, cert("Leaf", rootKey, null, null, false));
                try { PemIdentity.read(certificates, key, null); throw new AssertionError("accepted leaf CA"); }
                catch (IllegalArgumentException expected) { assertions++; }
                System.out.println("PASS " + algorithm + ": PEM import, TLS signing, intermediate chain, CA switch, invalid input");
            }
            System.out.println("PASS " + assertions + " assertions; original classes loaded with overlay first");
        } finally {
            try (var files = Files.list(temp)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(temp);
        }
    }
}
