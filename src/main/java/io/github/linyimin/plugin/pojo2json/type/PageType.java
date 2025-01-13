package io.github.linyimin.plugin.pojo2json.type;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @author yansq
 * @version V1.0
 * @package io.github.linyimin.plugin.pojo2json.type
 * @date 2025/1/10 14:30
 */
public class PageType implements SpecifyType {

    @Override
    public Object def() {
        return new Page<>(1, 10);
    }

    @Override
    public Object random() {
        return new Page<>(1, 10);
    }
}