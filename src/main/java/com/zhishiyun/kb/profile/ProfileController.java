package com.zhishiyun.kb.profile;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.profile.dto.PreferenceRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人中心 API。 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /** 个人资料。 */
    @GetMapping
    public Result<?> profile(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(profileService.profile(user.getUserId()));
    }

    /** 读取个人偏好（默认知识库等）。 */
    @GetMapping("/preferences")
    public Result<?> preferences(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(profileService.preferences(user.getUserId()));
    }

    /** 更新个人偏好。 */
    @PutMapping("/preferences")
    public Result<?> updatePreferences(Authentication auth, @Valid @RequestBody PreferenceRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(profileService.updatePreferences(user.getUserId(), request));
    }
}
