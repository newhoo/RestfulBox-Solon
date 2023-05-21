package io.github.newhoo.restkit.ext.solon.linemarker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import io.github.newhoo.restkit.common.RestItem;
import io.github.newhoo.restkit.config.ConfigHelper;
import io.github.newhoo.restkit.ext.solon.language.JavaLanguageResolver;
import io.github.newhoo.restkit.toolwindow.ToolWindowHelper;
import org.jetbrains.annotations.NotNull;

import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestMethodAnnotation.REQUEST_MAPPING;

/**
 * ServiceLineMarkerProvider
 *
 * @author newhoo
 * @date 2023/5/21
 * @since 1.0.0
 */
public class ServiceLineMarkerProvider implements LineMarkerProvider {

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (element instanceof PsiMethod && ConfigHelper.getCommonSetting(element.getProject()).isEnableMethodLineMarker()) {
            PsiMethod psiMethod = (PsiMethod) element;
            if (!psiMethod.hasAnnotation(REQUEST_MAPPING.getQualifiedName())) {
                return null;
            }
            PsiClass containingClass = psiMethod.getContainingClass();
            boolean flag = containingClass != null && containingClass.hasAnnotation("org.noear.solon.annotation.Controller");
            if (flag) {
                return new LineMarkerInfo<>(element, element.getTextRange(), ConfigHelper.NAVIGATE_ICON,
                                            psiElement -> ConfigHelper.NAVIGATE_TEXT,
                                            (e, elt) -> {
                                                ToolWindowHelper.navigateToTree(elt, () -> {
                                                    RestItem restItem = new JavaLanguageResolver().tryGenerateRestItem(element);
                                                    if (restItem != null) {
                                                        restItem.setProject(elt.getProject().getName());
                                                    }
                                                    return restItem;
                                                });
                                            },
                                            GutterIconRenderer.Alignment.LEFT, () -> "RestfulBox-Solon");
            }
        }
        return null;
    }
}
