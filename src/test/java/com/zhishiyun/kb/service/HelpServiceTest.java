package com.zhishiyun.kb.service;


import com.zhishiyun.kb.entity.HelpFaqEntity;
import com.zhishiyun.kb.mapper.HelpFaqMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** FAQ 五条可读验收。 */
class HelpServiceTest {

    @Test
    void shouldReturnFiveFaqsOrdered() {
        HelpFaqMapper mapper = Mockito.mock(HelpFaqMapper.class);
        HelpService service = new HelpService(mapper);
        Mockito.when(mapper.selectList(ArgumentMatchers.any())).thenReturn(Arrays.asList(
                faq(1, "如何开始提问？"),
                faq(2, "答案里的来源是什么？"),
                faq(3, "为什么有些知识搜不到？"),
                faq(4, "如何收藏常用内容？"),
                faq(5, "反馈「没帮助」会怎样？")
        ));
        List<Map<String, Object>> list = service.listFaqs("zh-CN");
        Assertions.assertEquals(5, list.size());
        Assertions.assertEquals("如何开始提问？", list.get(0).get("question"));
    }

    private HelpFaqEntity faq(int sort, String q) {
        HelpFaqEntity e = new HelpFaqEntity();
        e.setId((long) sort);
        e.setSortNo(sort);
        e.setQuestion(q);
        e.setAnswer("answer-" + sort);
        e.setLocale("zh-CN");
        return e;
    }
}
