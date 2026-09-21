package com.messaging.channel.imapsmtp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ImapSmtpProperties.class)
public class ImapSmtpConfig {}
