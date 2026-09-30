package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GoogleUserListCustomerTypeRequest(
    @NotNull Long audienceId,
    List<String> customerTypeCategories) {

  public GoogleUserListCustomerTypeRequest {
    customerTypeCategories =
        customerTypeCategories == null ? List.of() : List.copyOf(customerTypeCategories);
  }
}
