package addon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.*;
import org.bouncycastle.openssl.jcajce.*;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;

public record PemIdentity(PrivateKey key, X509Certificate[] chain) {
    static { Security.addProvider(new BouncyCastleProvider()); }

    public static String algorithm(PrivateKey key) {
        return switch (key.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            default -> throw new IllegalArgumentException("Supported CA private keys: RSA and EC.");
        };
    }

    public static PemIdentity read(Path certificates, Path privateKey, char[] password) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(certificates, StandardCharsets.UTF_8))) {
            Object item;
            while ((item = parser.readObject()) != null) {
                if (item instanceof X509CertificateHolder holder)
                    certs.add(new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder));
            }
        }
        PrivateKey key = null;
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(privateKey, StandardCharsets.UTF_8))) {
            Object item;
            while ((item = parser.readObject()) != null) {
                PrivateKey candidate = null;
                if (item instanceof PEMKeyPair pair) candidate = converter.getPrivateKey(pair.getPrivateKeyInfo());
                else if (item instanceof PrivateKeyInfo info) candidate = converter.getPrivateKey(info);
                else if (item instanceof PEMEncryptedKeyPair encrypted) {
                    requirePassword(password);
                    candidate = converter.getPrivateKey(encrypted.decryptKeyPair(
                        new JcePEMDecryptorProviderBuilder().setProvider("BC").build(password)).getPrivateKeyInfo());
                } else if (item instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
                    requirePassword(password);
                    candidate = converter.getPrivateKey(encrypted.decryptPrivateKeyInfo(
                        new JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC").build(password)));
                }
                if (candidate != null) {
                    if (key != null) throw new IllegalArgumentException("Choose a PEM containing exactly one private key.");
                    key = candidate;
                }
            }
        }
        if (key == null) throw new IllegalArgumentException("No private key found in PEM.");
        if (certs.isEmpty()) throw new IllegalArgumentException("No certificates found in PEM.");
        X509Certificate signer = null;
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signature = Signature.getInstance(algorithm(key), "BC");
        signature.initSign(key);
        signature.update(challenge);
        byte[] proof = signature.sign();
        for (X509Certificate cert : certs) {
            try {
                signature.initVerify(cert.getPublicKey());
                signature.update(challenge);
                if (signature.verify(proof)) {
                    if (signer != null) throw new IllegalArgumentException("Multiple certificates match the key.");
                    signer = cert;
                }
            } catch (InvalidKeyException ignored) { }
        }
        if (signer == null) throw new IllegalArgumentException("Private key does not match any certificate.");
        List<X509Certificate> ordered = new ArrayList<>();
        ordered.add(signer);
        certs.remove(signer);
        X509Certificate current = signer;
        while (!current.getIssuerX500Principal().equals(current.getSubjectX500Principal())) {
            X509Certificate parent = null;
            for (X509Certificate candidate : certs) {
                if (current.getIssuerX500Principal().equals(candidate.getSubjectX500Principal())) {
                    current.verify(candidate.getPublicKey());
                    parent = candidate;
                    break;
                }
            }
            if (parent == null) break; // An intermediate may be supplied without its root.
            ordered.add(parent);
            certs.remove(parent);
            current = parent;
        }
        if (!certs.isEmpty()) throw new IllegalArgumentException("PEM contains unrelated or duplicate certificates.");
        for (int i = 0; i < ordered.size(); i++) {
            X509Certificate cert = ordered.get(i);
            cert.checkValidity();
            if (cert.getBasicConstraints() < i) throw new IllegalArgumentException("Invalid CA basic constraints or path length.");
            boolean[] usage = cert.getKeyUsage();
            if (usage != null && (usage.length <= 5 || !usage[5]))
                throw new IllegalArgumentException("CA certificate does not permit certificate signing.");
            if (cert.getIssuerX500Principal().equals(cert.getSubjectX500Principal())) cert.verify(cert.getPublicKey());
        }
        return new PemIdentity(key, ordered.toArray(X509Certificate[]::new));
    }

    private static void requirePassword(char[] password) {
        if (password == null) throw new PasswordRequired();
    }
    public static final class PasswordRequired extends IllegalArgumentException { }
}
