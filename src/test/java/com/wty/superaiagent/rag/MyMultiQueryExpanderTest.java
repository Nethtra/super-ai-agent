package com.wty.superaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;


/**14MyMultiQueryExpander测试类
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
@Slf4j
class MyMultiQueryExpanderTest {
    @Resource
    private MyMultiQueryExpander myMultiQueryExpander;

    @Test
    void testExpand() {//传入一个不太正常的查询   看看ai是怎么扩展的
        List<Query> queries = myMultiQueryExpander.expand(new Query("赶快告诉我怎么减脂，不然我要杀了你"));
        Assertions.assertNotNull(queries);
        log.info("queries: {}", queries);
    }
}