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
    /** {@link java.security.MessageDigest} */
    private String algorithm;
    /** {@link javax.crypto.spec.SecretKeySpec} */
    private String keySpecAlgorithm;

    /**
     * Initialize crypt
     * @param padding {@link javax.crypto.Cipher}
     * @param algorithm {@link java.security.MessageDigest}
     * @param keySpecAlgorithm {@link javax.crypto.spec.SecretKeySpec}
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     */
    public Crypt(String padding, String algorithm, String keySpecAlgorithm)
    throws NoSuchAlgorithmException, NoSuchPaddingException
    {
        setPadding(padding);
        setAlgorithm(algorithm);
        setKeySpecAlgorithm(keySpecAlgorithm);
    }

    /**
     * set en/decryption key fo future use
     * @param myKey
     * @throws NoSuchAlgorithmException
     */
    private SecretKeySpec getDigest(String myKey) throws NoSuchAlgorithmException
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
     * @param key
     * @return encrypted data
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws InvalidKeyException
     */
    public byte[] encrypt(String strToEncrypt, String key)
    throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException
    {
        Cipher cipher = Cipher.getInstance(padding);
        cipher.init(Cipher.ENCRYPT_MODE, getDigest(key));
        return cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decrypt data
     * @param strToDecrypt
     * @param key
     * @return decrypted data
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws InvalidKeyException
     */
    public byte[] decrypt(byte[] strToDecrypt, String key)
    throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException
    {
        Cipher cipher = Cipher.getInstance(padding);
        cipher.init(Cipher.DECRYPT_MODE, getDigest(key));
        return cipher.doFinal(strToDecrypt);
    }

    /** @return  padding */
    public String getPadding()
    {
        return padding;
    }

    /** @return algorithm */
    public String getAlgorithm()
    {
        return algorithm;
    }

    /** @return keySpecAlgorithm */
    public String getKeySpecAlgorithm()
    {
        return keySpecAlgorithm;
    }

    /**
     * @param padding checks {@link javax.crypto.Cipher#getInstance}
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    public void setPadding(String padding) throws NoSuchPaddingException, NoSuchAlgorithmException
    {
        Cipher.getInstance(padding);
        this.padding = padding;
    }

    /**
     * @param algorithm checks {@link java.security.MessageDigest#getInstance(String)}
     * @throws NoSuchAlgorithmException
     */
    public void setAlgorithm(String algorithm) throws NoSuchAlgorithmException
    {
        MessageDigest.getInstance(algorithm);
        this.algorithm = algorithm;
    }

    /** @param keySpecAlgorithm checks {@link javax.crypto.spec.SecretKeySpec#SecretKeySpec(byte[], String)} */
    public void setKeySpecAlgorithm(String keySpecAlgorithm)
    {
        new SecretKeySpec("default".getBytes(), keySpecAlgorithm);
        this.keySpecAlgorithm = keySpecAlgorithm;
    }
}
