package com.wty.superaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 王天一
 * @version 1.0
 */
@Slf4j
class TerminalOperationToolTest {

    @Test
    void executeCommand() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        String result = terminalOperationTool.executeCommand("java -version");
        Assertions.assertNotNull(result);
        log.info(result);
    }
}