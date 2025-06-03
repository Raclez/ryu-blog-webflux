package com.ryu.blog.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.digest.BCrypt;

import java.security.KeyPair;
import java.util.Base64;

public class CryptoUtil {

    /** ========== BCrypt（用于密码） ========== */
    public static String bcryptEncode(String plainText) {
        return BCrypt.hashpw(plainText, BCrypt.gensalt());
    }

    public static boolean bcryptVerify(String plainText, String hashed) {
        return BCrypt.checkpw(plainText, hashed);
    }

    /** ========== MD5/SHA摘要加密（不可逆） ========== */
    public static String md5(String data) {
        return SecureUtil.md5(data);
    }

    public static String sha256(String data) {
        return SecureUtil.sha256(data);
    }

    /** ========== RSA 加密/解密（可逆） ========== */
    private static final RSA rsa;

    static {
        rsa = new RSA(); // 自动生成公钥私钥
    }

    public static String getPublicKey() {
        return Base64.getEncoder().encodeToString(rsa.getPublicKey().getEncoded());
    }

    public static String getPrivateKey() {
        return Base64.getEncoder().encodeToString(rsa.getPrivateKey().getEncoded());
    }

    public static String rsaEncrypt(String data) {
        return rsa.encryptBase64(data, KeyType.PublicKey);
    }

    public static String rsaDecrypt(String encryptedData) {
        return rsa.decryptStr(encryptedData, KeyType.PrivateKey);
    }

    /** ========== 工具扩展：使用指定密钥 ========== */
    public static RSA createRSA(String privateKeyBase64, String publicKeyBase64) {
        return new RSA(privateKeyBase64, publicKeyBase64);
    }

    public static KeyPair generateKeyPair() {
        return SecureUtil.generateKeyPair("RSA");
    }
}