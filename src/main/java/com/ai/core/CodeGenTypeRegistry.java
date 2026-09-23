package com.ai.core;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.enums.CodeGenTypeEnum;

import java.util.Map;

/**
 * 生成类型 → 处理器注册表。新增一种生成类型只需实现 {@link CodeGenTypeHandler}
 * 并在此注册，无需再改动 Facade / Executor 里的分发逻辑。
 */
public final class CodeGenTypeRegistry {

    private static final Map<CodeGenTypeEnum, CodeGenTypeHandler> HANDLERS = Map.of(
            CodeGenTypeEnum.HTML, new HtmlCodeGenTypeHandler(),
            CodeGenTypeEnum.MULTI_FILE, new MultiFileCodeGenTypeHandler()
    );

    private CodeGenTypeRegistry() {
    }

    public static CodeGenTypeHandler require(CodeGenTypeEnum type) {
        CodeGenTypeHandler handler = HANDLERS.get(type);
        if (handler == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的生成类型：" + type.getValue());
        }
        return handler;
    }
}
