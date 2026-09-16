package com.messaging.admin.controller;
import com.messaging.admin.dto.PlatformUserResponse;
import com.messaging.admin.service.PlatformUserManagementService;
import com.messaging.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/platform/users") @RequiredArgsConstructor
public class PlatformUserManagementController {
 private final PlatformUserManagementService service;
 @GetMapping public ResponseEntity<ApiResponse<Page<PlatformUserResponse>>> list(@RequestParam(defaultValue="") String search,@PageableDefault(size=20,sort="email") Pageable pageable){
  return ResponseEntity.ok(ApiResponse.success(200,service.list(search,pageable),"Users fetched"));
 }
}
