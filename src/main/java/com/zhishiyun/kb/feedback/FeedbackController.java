package com.zhishiyun.kb.feedback;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.feedback.dto.HelpfulRequest;
import com.zhishiyun.kb.feedback.dto.RatingRequest;
import com.zhishiyun.kb.feedback.dto.UnhelpfulRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/helpful")
    public Result<Void> helpful(Authentication auth, @Valid @RequestBody HelpfulRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        feedbackService.helpful(user.getUserId(), request);
        return new Result<Void>(0, "感谢反馈，我们会尽快优化", null);
    }

    @PostMapping("/unhelpful")
    public Result<Void> unhelpful(Authentication auth, @Valid @RequestBody UnhelpfulRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        feedbackService.unhelpful(user.getUserId(), request);
        return new Result<Void>(0, "感谢反馈，我们会尽快优化", null);
    }

    @PostMapping("/rating")
    public Result<Void> rating(Authentication auth, @Valid @RequestBody RatingRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        feedbackService.rating(user.getUserId(), request);
        return new Result<Void>(0, "感谢反馈，我们会尽快优化", null);
    }
}
