package util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** En/Decryption */
public class Crypt
{
    /** {@link javax.crypto.Cipher} */
    private String padding;
    /** crypt key */
    private final SecretKeySpec digest;

    /**
     * Initialize crypt
     * @param padding {@link javax.crypto.Cipher}
     * @param algorithm {@link java.security.MessageDigest}
     * @param keySpecAlgorithm {@link javax.crypto.spec.SecretKeySpec}
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     */
    public Crypt(String padding, String algorithm, String keySpecAlgorithm, String key)
    throws NoSuchAlgorithmException, NoSuchPaddingException
    {
        setPadding(padding);
        digest = getDigest(algorithm, keySpecAlgorithm, key);
    }

    /**
     * Initialize crypt
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     */
    public Crypt(String key)
    throws NoSuchAlgorithmException, NoSuchPaddingException
    {
        this("AES/ECB/PKCS5Padding", "SHA-1", "AES", key);
    }

    /**
     * set en/decryption key fo future use
     * @param myKey
     * @throws NoSuchAlgorithmException
     */
    private static SecretKeySpec getDigest(String algorithm, String keySpecAlgorithm, String myKey)
    throws NoSuchAlgorithmException
    {
        byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance(algorithm);
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16);
        return new SecretKeySpec(key, keySpecAlgorithm);
    }

    /**
     * Encrypt data
     * @param strToEncrypt
     * @return encrypted data
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws InvalidKeyException
     */
    public byte[] encrypt(String strToEncrypt)
    throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException
    {
        Cipher cipher = Cipher.getInstance(padding);
        cipher.init(Cipher.ENCRYPT_MODE, digest);
        return cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decrypt data
     * @param strToDecrypt
     * @return decrypted data
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws InvalidKeyException
     */
    public byte[] decrypt(byte[] strToDecrypt)
    throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException
    {
        Cipher cipher = Cipher.getInstance(padding);
        cipher.init(Cipher.DECRYPT_MODE, digest);
        return cipher.doFinal(strToDecrypt);
    }

    /** @return  padding */
    public String getPadding()
    {
        return padding;
    }

    /**
     * @param padding checks {@link javax.crypto.Cipher#getInstance}
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    private void setPadding(String padding) throws NoSuchPaddingException, NoSuchAlgorithmException
    {
        Cipher.getInstance(padding);
        this.padding = padding;
    }
}
