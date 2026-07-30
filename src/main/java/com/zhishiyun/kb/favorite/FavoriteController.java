package com.zhishiyun.kb.favorite;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.favorite.dto.FavoriteAnswerRequest;
import com.zhishiyun.kb.favorite.dto.FavoriteDocumentRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping("/documents")
    public Result<?> docList(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(favoriteService.listDocs(user.getUserId()));
    }

    @PostMapping("/documents")
    public Result<?> saveDoc(Authentication auth, @Valid @RequestBody FavoriteDocumentRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        favoriteService.saveDoc(user.getUserId(), request);
        return Result.ok(null);
    }

    @DeleteMapping("/documents/{docId}")
    public Result<?> deleteDoc(Authentication auth, @PathVariable Long docId) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        favoriteService.deleteDoc(user.getUserId(), docId);
        return Result.ok(null);
    }

    @GetMapping("/answers")
    public Result<?> answerList(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(favoriteService.listAnswers(user.getUserId()));
    }

    @PostMapping("/answers")
    public Result<?> saveAnswer(Authentication auth, @Valid @RequestBody FavoriteAnswerRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        favoriteService.saveAnswer(user.getUserId(), request);
        return Result.ok(null);
    }

    @DeleteMapping("/answers/{id}")
    public Result<?> deleteAnswer(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        favoriteService.deleteAnswer(user.getUserId(), id);
        return Result.ok(null);
    }
}
