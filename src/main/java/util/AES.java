package util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class AES
{
    private static SecretKeySpec secretKey;
    private static byte[] key;

    public static AES setKey(String myKey)
    {
        MessageDigest sha = null;
        try
        {
            key = myKey.getBytes(StandardCharsets.UTF_8);
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static byte[] encrypt(String strToEncrypt, String key) throws Exception
    {
        setKey(key);
        return encrypt(strToEncrypt);
    }

    public static byte[] encrypt(String strToEncrypt) throws Exception
    {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));
    }

    public static String decrypt(byte[] strToDecrypt, String key) throws Exception
    {
        setKey(key);
        return decrypt(strToDecrypt);
    }

    public static String decrypt(byte[] strToDecrypt) throws Exception
    {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(strToDecrypt));
    }
}
