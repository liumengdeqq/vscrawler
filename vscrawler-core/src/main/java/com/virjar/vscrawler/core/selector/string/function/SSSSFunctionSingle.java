package com.virjar.vscrawler.core.selector.string.function;

import java.util.List;

import com.google.common.base.Preconditions;
import com.virjar.vscrawler.core.selector.string.Strings;
import com.virjar.vscrawler.core.selector.string.syntax.StringContext;
import com.virjar.vscrawler.core.selector.string.syntax.SyntaxNode;

/**
 * Created by virjar on 17/7/8.
 */
public abstract class SSSSFunctionSingle extends FirstStringsFunction {
    @Override
    protected Strings handle(Strings input, StringContext stringContext, List<SyntaxNode> params) {
        Preconditions.checkArgument(params.size() >= 3, determineFunctionName() + " must have 2 parameters at last");

        Object secondObject = params.get(1).calculate(stringContext);
        if (!(secondObject instanceof CharSequence)) {
            throw new IllegalStateException(
                    determineFunctionName() + " second parameter must be string,now is : " + secondObject);
        }

        Object thirdObject = params.get(2).calculate(stringContext);
        if (!(thirdObject instanceof CharSequence)) {
            throw new IllegalStateException(
                    determineFunctionName() + " third parameter must be string,now is : " + thirdObject);
        }

        Strings ret = new Strings();
        String secondStr = secondObject.toString();
        String thirdStr = thirdObject.toString();
        for (String str : input) {
            ret.add(handle(str, secondStr, thirdStr));
        }
        return ret;
    }

    protected  abstract String handle(String input, String second, String third);
}