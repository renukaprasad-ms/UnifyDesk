package com.messaging.emailprovider.validation.core;

import java.util.List;

public record DomainValidationSnapshot(
    String domain,
    boolean routable,
    boolean nullMx,
    boolean mxFound,
    boolean dnsLookupFailed,
    boolean disposable,
    Boolean catchAll,
    List<String> hosts) {}
