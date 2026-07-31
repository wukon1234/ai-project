package com.zhishiyun.kb.help;

import com.zhishiyun.kb.common.Result;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 帮助页 API：FAQ 列表。 */
@RestController
@RequestMapping("/api/v1/help")
@RequiredArgsConstructor
public class HelpController {

    private final HelpService helpService;

    @GetMapping("/faqs")
    public Result<List<Map<String, Object>>> faqs(
            @RequestParam(value = "locale", defaultValue = "zh-CN") String locale) {
        return Result.ok(helpService.listFaqs(locale));
    }
}
