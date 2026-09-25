package com.messaging.emailprovider.dns;

import java.util.Set;

public interface DnsResolver {

  Set<String> findTxtRecords(String name);
}
