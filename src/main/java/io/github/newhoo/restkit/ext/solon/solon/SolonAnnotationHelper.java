package io.github.newhoo.restkit.ext.solon.solon;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import io.github.newhoo.restkit.ext.solon.MethodPath;
import io.github.newhoo.restkit.ext.solon.helper.PsiAnnotationHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestMethodAnnotation.REQUEST_MAPPING;

public class SolonAnnotationHelper {

    /**
     * 类上的注解
     */
    public static List<MethodPath> getTypeMethodPaths(PsiClass psiClass) {
        return getMethodPaths(solonMappingAnno -> PsiAnnotationHelper.getInheritedAnnotation(psiClass, solonMappingAnno.getQualifiedName()));
    }

    /**
     * 方法上的注解
     */
    public static List<MethodPath> getMethodMethodPaths(PsiMethod psiMethod) {
        return getMethodPaths(solonMappingAnno -> PsiAnnotationHelper.getInheritedAnnotation(psiMethod, solonMappingAnno.getQualifiedName()));
    }

    private static List<MethodPath> getMethodPaths(@NotNull Function<SolonRequestMethodAnnotation, PsiAnnotation> getAnno) {
        PsiAnnotation requestMappingAnnotation = getAnno.apply(REQUEST_MAPPING);
        if (requestMappingAnnotation == null) {
            return Collections.emptyList();
        }

        String path = "";
        List<String> methodList;

        List<String> pathList = PsiAnnotationHelper.getAnnotationAttributeValues(requestMappingAnnotation, "value");
        if (pathList.isEmpty()) {
            pathList = PsiAnnotationHelper.getAnnotationAttributeValues(requestMappingAnnotation, "path");
        }
        if (!pathList.isEmpty()) {
            path = pathList.get(0);
        }

        methodList = PsiAnnotationHelper.getAnnotationAttributeValues(requestMappingAnnotation, "method")
                                        .stream()
                                        .map(method -> method.replace("MethodType.", ""))
                                        .collect(Collectors.toList());

        for (SolonRequestMethodAnnotation annotation : SolonRequestMethodAnnotation.values()) {
            if (annotation.getMethod() != null) {
                if (getAnno.apply(annotation) != null) {
                    methodList.add(annotation.getMethod());
                }
            }
        }

        List<MethodPath> mappingList = new ArrayList<>(4);
        if (methodList.isEmpty()) {
            mappingList.add(new MethodPath(path, null));
        } else {
            for (String method : methodList) {
                mappingList.add(new MethodPath(path, method));
            }
        }

        return mappingList;
    }
}