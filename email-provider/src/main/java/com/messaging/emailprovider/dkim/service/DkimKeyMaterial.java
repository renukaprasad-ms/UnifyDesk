package com.messaging.emailprovider.dkim.service;

public record DkimKeyMaterial(String privateKey, String publicKey) {}
