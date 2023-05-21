package io.github.newhoo.restkit.ext.solon.language;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.newhoo.restkit.common.KV;
import io.github.newhoo.restkit.common.RestItem;
import io.github.newhoo.restkit.ext.solon.MethodPath;
import io.github.newhoo.restkit.ext.solon.solon.SolonAnnotationHelper;
import io.github.newhoo.restkit.ext.solon.solon.SolonControllerAnnotation;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JavaLanguageResolver, will work when Java enabled
 *
 * @author newhoo
 * @since 1.0.0
 */
public class JavaLanguageResolver extends BaseLanguageResolver {

    public RestItem tryGenerateRestItem(@NotNull PsiElement psiElement) {
        PsiMethod psiMethod;
        if (psiElement instanceof PsiMethod) {
            psiMethod = (PsiMethod) psiElement;
        } else if (psiElement.getParent() instanceof PsiMethod) {
            psiMethod = (PsiMethod) psiElement.getParent();
        } else {
            return null;
        }
        List<MethodPath> typeMethodPaths = SolonAnnotationHelper.getTypeMethodPaths(psiMethod.getContainingClass());
        List<MethodPath> methodMethodPaths = SolonAnnotationHelper.getMethodMethodPaths(psiMethod);
        return combineFirstRestItem(typeMethodPaths, methodMethodPaths, psiMethod, "");
    }

    @Override
    public List<RestItem> findRestItemListInModule(Module module, GlobalSearchScope globalSearchScope) {
        List<RestItem> itemList = new ArrayList<>();
        SolonControllerAnnotation[] supportedAnnotations = SolonControllerAnnotation.values();
//        Set<String> filterClassQualifiedNames = new HashSet<>();
        for (SolonControllerAnnotation controllerAnnotation : supportedAnnotations) {
            // java: 标注了 (Rest)Controller 注解的类，即 Controller 类
            Collection<PsiAnnotation> psiAnnotations = JavaAnnotationIndex.getInstance().get(controllerAnnotation.getShortName(), module.getProject(), globalSearchScope);
            for (PsiAnnotation psiAnnotation : psiAnnotations) {
                if (!controllerAnnotation.getQualifiedName().equals(psiAnnotation.getQualifiedName())) {
                    continue;
                }
                PsiModifierList psiModifierList = (PsiModifierList) psiAnnotation.getParent();
                PsiElement psiElement = psiModifierList.getParent();

                if (psiElement instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) psiElement;
//                    if (filterClassQualifiedNames.contains(psiClass.getQualifiedName())) {
//                        continue;
//                    }
                    List<RestItem> serviceItemList = getRequestItemList(psiClass, module);
                    serviceItemList.forEach(e -> e.setPackageName(psiClass.getQualifiedName()));
                    itemList.addAll(serviceItemList);
                }
            }
        }
        return itemList;
    }

    private List<RestItem> getRequestItemList(PsiClass psiClass, Module module) {
        List<PsiMethod> psiMethods = new ArrayList<>(Arrays.asList(psiClass.getMethods()));
        for (PsiClass aSuper : psiClass.getSupers()) {
            if (!"java.lang.Object".equals(aSuper.getQualifiedName())) {
                psiMethods.addAll(Arrays.asList(aSuper.getMethods()));
            }
        }
        if (psiMethods.size() == 0) {
            return Collections.emptyList();
        }

        List<RestItem> itemList = new ArrayList<>();
        List<MethodPath> typeMethodPaths = SolonAnnotationHelper.getTypeMethodPaths(psiClass);

        for (PsiMethod psiMethod : psiMethods) {
            List<MethodPath> methodMethodPaths = SolonAnnotationHelper.getMethodMethodPaths(psiMethod);
            itemList.addAll(combineTypeAndMethod(typeMethodPaths, methodMethodPaths, psiMethod, module));
        }
        return itemList;
    }

    @NotNull
    @Override
    public List<KV> buildHeaders(@NotNull PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            return Collections.emptyList();
        }
        PsiMethod psiMethod = (PsiMethod) psiElement;
        return buildHeaderString(psiMethod);
    }

    @NotNull
    @Override
    public List<KV> buildParams(@NotNull PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            return Collections.emptyList();
        }
        PsiMethod psiMethod = (PsiMethod) psiElement;
        return buildParamString(psiMethod);
    }

    @NotNull
    @Override
    public String buildRequestBodyJson(@NotNull PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            return "";
        }
        PsiMethod psiMethod = (PsiMethod) psiElement;
        String s = buildRequestBodyJson(psiMethod);
        return Objects.nonNull(s) ? s : "";
    }

    @NotNull
    @Override
    public String buildDescription(@NotNull PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            return "";
        }
        PsiMethod psiMethod = (PsiMethod) psiElement;

        String restName = null;
        String location;
        if (psiMethod.getDocComment() != null) {
            restName = Arrays.stream(psiMethod.getDocComment().getDescriptionElements())
                             .filter(e -> e instanceof PsiDocToken)
                             .filter(e -> StringUtils.isNotBlank(e.getText()))
                             .findFirst()
                             .map(e -> e.getText().trim()).orElse(null);
        }
        location = psiMethod.getContainingClass().getName().concat("#").concat(psiMethod.getName());
        if (StringUtils.isNotEmpty(restName)) {
            location = location.concat("#").concat(restName);
        }
        return location;
    }
}
