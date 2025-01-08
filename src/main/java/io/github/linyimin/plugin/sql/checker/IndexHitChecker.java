package io.github.linyimin.plugin.sql.checker;

import io.github.linyimin.plugin.sql.checker.enums.CheckScopeEnum;

/**
 * 暂时注释，待oracle 执行计划逻辑完成
 * @author banzhe
 **/
//public class IndexHitChecker extends Checker {
public class IndexHitChecker {
    //@Override
    CheckScopeEnum scope() {
        return CheckScopeEnum.index_hit;
    }
}
