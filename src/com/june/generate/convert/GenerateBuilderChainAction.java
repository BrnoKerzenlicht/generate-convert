package com.june.generate.convert;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GenerateBuilderChainAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        //这里的思路是替换整个psiMethod，方法的声明是写死的。可能存在坑点，更好的是利用 PsiCodeBlock
        PsiElement psiElement = getPsiElement(event);

        assert psiElement != null;

        generateBuilderChain(psiElement);
    }

    private void generateBuilderChain(PsiElement psiElement) {
        WriteCommandAction.runWriteCommandAction(psiElement.getProject(), () -> {
            String string = generateBuilderChainCode(psiElement);

            if (StringUtils.isBlank(string)) {
                return;
            }

            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiElement.getProject());

            PsiElement newCode = elementFactory.createStatementFromText(string, null);

            psiElement.replace(newCode);
        });
    }

    private String generateBuilderChainCode(PsiElement psiElement) {
        if (Objects.nonNull(PsiTreeUtil.getParentOfType(psiElement, PsiVariable.class))) {
            PsiVariable psiVariable = PsiTreeUtil.getParentOfType(psiElement, PsiVariable.class);
            return generateBuilderChainCode(psiVariable);
        } else if (Objects.nonNull(PsiTreeUtil.getParentOfType(psiElement, PsiExpression.class))) {
            PsiExpression psiExpression = PsiTreeUtil.getParentOfType(psiElement, PsiExpression.class);
            return generateBuilderChainCode(psiExpression);
        }

        return null;
    }

    private String generateBuilderChainCode(PsiExpression psiExpression) {
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(psiExpression.getProject());
        PsiClass[] psiClassArray = shortNamesCache.getClassesByName(psiExpression.getText(), GlobalSearchScope.allScope(psiExpression.getProject()));

        if (psiClassArray.length == 0) {
            return null;
        }

        PsiClass psiClass = psiClassArray[0];

        StringBuilder stringBuilder = new StringBuilder(psiExpression.getText()).append(".builder()\n");

        for (PsiField psiField : psiClass.getAllFields()) {
            stringBuilder.append(".").append(psiField.getName()).append("()").append("\n");
        }

        stringBuilder.append(".build();\n");

        return stringBuilder.toString();
    }

    private String generateBuilderChainCode(PsiVariable psiVariable) {
        if (Objects.isNull(psiVariable)) {
            return null;
        }

        PsiType variableType = psiVariable.getType();

        String variableClassText = variableType.getPresentableText();
        //带package的class名称
        String variableClassWithPackage = variableType.getInternalCanonicalText();
        //为了解析字段，这里需要加载参数的class
        JavaPsiFacade facade = JavaPsiFacade.getInstance(psiVariable.getProject());
        PsiClass variableClass = facade
                .findClass(variableClassWithPackage, GlobalSearchScope.allScope(psiVariable.getProject()));
        if (variableClass == null) {
            return null;
        }

        StringBuilder variableBuilder = new StringBuilder().append(psiVariable.getName()).append(" = ");

        variableBuilder.append(variableClassText).append(".builder()").append("\n");

        for (PsiField psiField : variableClass.getAllFields()) {
            variableBuilder.append(".").append(psiField.getName()).append("()").append("\n");
        }

        variableBuilder.append(".build();\n");

        return variableBuilder.toString();
    }

    private PsiVariable getPsiVariableFromContext(AnActionEvent event) {
        PsiElement elementAt = getPsiElement(event);
        if (elementAt == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PsiVariable.class);
    }

    private PsiCodeBlock getPsiCodeBlockFromContext(AnActionEvent event) {
        PsiElement elementAt = getPsiElement(event);
        if (elementAt == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PsiCodeBlock.class);
    }

    private PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }
        //用来获取当前光标处的PsiElement
        int offset = editor.getCaretModel().getOffset();
        return psiFile.findElementAt(offset);
    }
}
