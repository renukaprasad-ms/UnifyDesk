package com.messaging.emailprovider.common.credential;

public interface CredentialCipher {

  String encrypt(String plaintext);

  String decrypt(String ciphertext);
}
