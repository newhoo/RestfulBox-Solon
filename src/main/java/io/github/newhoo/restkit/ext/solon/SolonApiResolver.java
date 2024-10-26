package io.github.newhoo.restkit.ext.solon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.newhoo.restkit.ext.solon.helper.PsiAnnotationHelper;
import io.github.newhoo.restkit.ext.solon.helper.PsiClassHelper;
import io.github.newhoo.restkit.ext.solon.solon.SolonAnnotationHelper;
import io.github.newhoo.restkit.ext.solon.solon.SolonControllerAnnotation;
import io.github.newhoo.restkit.ext.solon.util.TypeUtils;
import io.github.newhoo.restkit.open.LanguageResolver;
import io.github.newhoo.restkit.open.ParamResolver;
import io.github.newhoo.restkit.open.RequestResolver;
import io.github.newhoo.restkit.open.ep.LanguageResolverProvider;
import io.github.newhoo.restkit.open.ep.RestfulResolverProvider;
import io.github.newhoo.restkit.open.model.JsonStruct;
import io.github.newhoo.restkit.open.model.KV;
import io.github.newhoo.restkit.open.model.PsiRestItem;
import io.github.newhoo.restkit.open.model.RestItem;
import io.github.newhoo.restkit.open.model.SimpleLineMarkerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestMethodAnnotation.REQUEST_MAPPING;
import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestParamAnnotation.PATH_VARIABLE;
import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestParamAnnotation.REQUEST_BODY;
import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestParamAnnotation.REQUEST_COOKIE;
import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestParamAnnotation.REQUEST_HEADER;
import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestParamAnnotation.REQUEST_PARAM;

/**
 * solon service scanner
 *
 * @since 1.0.0
 */
public class SolonApiResolver implements RequestResolver, ParamResolver {

    @NotNull
    @Override
    public String getFrameworkName() {
        return "Solon";
    }

    @Override
    public @NotNull String getDescription() {
        return "- 支持 Solon 接口扫描和在线调试，识别 @Controller等注解<br/>- 支持 Java 语言";
    }

    public static Optional<LanguageResolver> getLanguageResolver(@NotNull PsiElement psiElement) {
        return LanguageResolverProvider.EP_NAME.getExtensionList()
                                               .stream()
                                               .filter(Objects::nonNull)
                                               .map(LanguageResolverProvider::createLanguageResolver)
                                               .filter(languageResolver -> languageResolver.getLanguage().getID().equals(psiElement.getLanguage().getID()))
                                               .findFirst();
    }

    @Override
    public @NotNull List<RestItem> findRestItemListInModule(Module module, GlobalSearchScope globalSearchScope) {
        List<RestItem> itemList = new ArrayList<>();
        SolonControllerAnnotation[] supportedAnnotations = SolonControllerAnnotation.values();
//        Set<String> filterClassQualifiedNames = new HashSet<>();
        for (SolonControllerAnnotation controllerAnnotation : supportedAnnotations) {
            // java: 标注了 (Rest)Controller 注解的类，即 Controller 类
            Collection<PsiAnnotation> psiAnnotations = JavaAnnotationIndex.getInstance().getAnnotations(controllerAnnotation.getShortName(), module.getProject(), globalSearchScope);
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

    @Override
    public boolean canNavigateToTree(@NotNull PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            return false;
        }
        PsiMethod psiMethod = (PsiMethod) psiElement;
        if (!psiMethod.hasAnnotation(REQUEST_MAPPING.getQualifiedName())) {
            return false;
        }
        PsiClass containingClass = psiMethod.getContainingClass();
        return containingClass != null && containingClass.hasAnnotation("org.noear.solon.annotation.Controller");
    }

    @Override
    public SimpleLineMarkerInfo tryGenerateLineMarker(@NotNull PsiElement psiElement) {
        return Optional.of(psiElement)
                       .filter(e -> canNavigateToTree(e))
                       .flatMap(e -> Arrays.stream(e.getChildren())
                                           .filter(child -> child instanceof PsiIdentifier)
                                           .findFirst()
                                           .map(child -> {
                                               // fix: Performance warning: LineMarker is supposed to be registered for leaf elements only. 返回 PsiIdentifier
                                               SimpleLineMarkerInfo simpleLineMarkerInfo = new SimpleLineMarkerInfo(child, child.getTextRange());
                                               simpleLineMarkerInfo.setNavElementSupplier(() -> child.getParent());
                                               return simpleLineMarkerInfo;
                                           }))
                       .orElse(null);
    }

    @Override
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

        Optional<LanguageResolver> languageResolver = getLanguageResolver(psiClass);
        if (languageResolver.map(l -> l.isIgnored(psiClass)).orElse(false)) {
            return Collections.emptyList();
        }
        String groupName = languageResolver.flatMap(l -> l.findApiGroup(psiClass)).orElse(psiClass.getQualifiedName());
        Set<String> classTags = languageResolver.map(l -> l.findApiTags(psiClass)).orElse(Collections.emptySet());

        List<RestItem> itemList = new ArrayList<>();
        List<MethodPath> typeMethodPaths = SolonAnnotationHelper.getTypeMethodPaths(psiClass);

        for (PsiMethod psiMethod : psiMethods) {
            if (languageResolver.map(l -> l.isIgnored(psiMethod)).orElse(false)) {
                continue;
            }
            List<MethodPath> methodMethodPaths = SolonAnnotationHelper.getMethodMethodPaths(psiMethod);
            List<RestItem> restItems = combineTypeAndMethod(typeMethodPaths, methodMethodPaths, psiMethod, module);

            String apiName = languageResolver.flatMap(l -> l.findApiName(psiMethod)).orElseGet(psiMethod::getName);
            String description = languageResolver.flatMap(l -> l.findApiDescription(psiMethod)).orElse("");
            Set<String> methodTags = languageResolver.map(l -> l.findApiTags(psiMethod)).orElse(Collections.emptySet());
            for (RestItem item : restItems) {

                item.setName(apiName);
                item.setDescription(description);
                item.setFolderPath(item.getModuleName(), groupName, true);
                item.setTags(new LinkedHashSet<String>(org.apache.commons.collections.CollectionUtils.union(classTags, methodTags)));
            }

            itemList.addAll(restItems);
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

    @Override
    public @NotNull List<JsonStruct> buildParamStruct(@NotNull PsiElement psiElement) {
        return List.of();
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

    @Override
    public String buildResponseBodyJson(@NotNull PsiElement psiElement) {
        return "";
    }

    @Override
    public JsonStruct buildRequestBodyStruct(PsiElement psiElement) {
        return null;
    }

    @Override
    public JsonStruct buildResponseBodyStruct(PsiElement psiElement) {
        return null;
    }

    @NotNull
    public RestItem createRestServiceItem(@NotNull Module module, PsiElement psiElement, @NotNull String typePath, @NotNull String methodPath, String method) {
        String requestPath = getCombinedPath(typePath, methodPath);
        return new PsiRestItem(requestPath, method, module.getName(), getFrameworkName(), psiElement, this);
    }

    @NotNull
    public RestItem createRestServiceItem(@NotNull Module module, PsiElement psiElement, @NotNull String path, String method) {
        return new PsiRestItem(path, method, module.getName(), getFrameworkName(), psiElement, this);
    }

    public List<RestItem> combineTypeAndMethod(List<MethodPath> typeMethodPaths, List<MethodPath> methodMethodPaths, PsiElement psiElement, Module module) {
        List<RestItem> itemList = new ArrayList<>();
        for (MethodPath methodPath : methodMethodPaths) {
            if (typeMethodPaths.isEmpty()) {
                RestItem item = createRestServiceItem(module, psiElement, "", methodPath.getPath(), methodPath.getMethod());
                itemList.add(item);
            } else {
                for (MethodPath typeMethodPath : typeMethodPaths) {
                    String combinedPath = getCombinedPath(typeMethodPath.getPath(), methodPath.getPath());
                    String typeMethod = typeMethodPath.getMethod();

                    if (typeMethod != null && !typeMethod.equals(methodPath.getMethod())) {
                        RestItem item = createRestServiceItem(module, psiElement, combinedPath, typeMethod);
                        itemList.add(item);
                    }

                    RestItem item = createRestServiceItem(module, psiElement, combinedPath, methodPath.getMethod());
                    itemList.add(item);
                }
            }
        }
        return itemList;
    }

    public RestItem combineFirstRestItem(List<MethodPath> typeMethodPaths, List<MethodPath> methodMethodPaths, PsiElement psiElement, String moduleName) {
        if (methodMethodPaths.isEmpty()) {
            return null;
        }
        MethodPath methodPath = methodMethodPaths.get(0);
        if (typeMethodPaths.isEmpty()) {
            String requestPath = getCombinedPath("", methodPath.getPath());
            return new PsiRestItem(requestPath, methodPath.getMethod(), moduleName, getFrameworkName(), psiElement, this);
        } else {
            MethodPath typeMethodPath = typeMethodPaths.get(0);
            String combinedPath = getCombinedPath(typeMethodPath.getPath(), methodPath.getPath());
            String typeMethod = typeMethodPath.getMethod();

            if (typeMethod != null && !typeMethod.equals(methodPath.getMethod())) {
                return new PsiRestItem(combinedPath, typeMethod, moduleName, getFrameworkName(), psiElement, this);
            }

            return new PsiRestItem(combinedPath, methodPath.getMethod(), moduleName, getFrameworkName(), psiElement, this);
        }
    }

    public List<KV> buildHeaderString(PsiMethod psiMethod) {
        List<KV> list = new ArrayList<>();
        PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
        for (PsiParameter psiParameter : psiParameters) {
            {
                PsiAnnotation requestHeaderAnno = psiParameter.getAnnotation(REQUEST_HEADER.getQualifiedName());
                if (requestHeaderAnno != null) {
                    String headerName = ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(requestHeaderAnno, "value"),
                            ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(requestHeaderAnno, "name"), psiParameter.getName()
                            ));
                    PsiClass fieldClass = PsiClassHelper.findPsiClass(psiParameter.getType().getCanonicalText(), psiMethod.getProject());
                    if (fieldClass != null && fieldClass.isEnum()) {
                        PsiField[] enumFields = fieldClass.getAllFields();
                        list.add(new KV(headerName, enumFields.length > 1 ? enumFields[0].getName() : ""));
                    } else {
                        Object fieldDefaultValue = TypeUtils.getExampleValue(psiParameter.getType().getPresentableText(), true);
                        list.add(new KV(headerName, String.valueOf(fieldDefaultValue)));
                    }
                }
            }
            {
                PsiAnnotation requestHeaderAnno = psiParameter.getAnnotation(REQUEST_COOKIE.getQualifiedName());
                if (requestHeaderAnno != null) {
                    String headerName = ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(requestHeaderAnno, "value"),
                            ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(requestHeaderAnno, "name"), psiParameter.getName()
                            ));
                    Object fieldDefaultValue = TypeUtils.getExampleValue(psiParameter.getType().getPresentableText(), true);
                    list.add(new KV("Cookie", headerName + "=" + fieldDefaultValue));
                }
            }
        }
        return list;
    }

    public List<KV> buildParamString(PsiMethod psiMethod) {
        List<KV> list = new ArrayList<>();

        List<Parameter> parameterList = getParameterList(psiMethod);

        // 拼接参数
        for (Parameter parameter : parameterList) {
            String paramType = parameter.getParamType();

            // 数组|集合
            if (TypeUtils.isArray(paramType) || TypeUtils.isList(paramType)) {
                paramType = TypeUtils.isArray(paramType)
                        ? paramType.replace("[]", "")
                        : paramType.contains("<")
                        ? paramType.substring(paramType.indexOf("<") + 1, paramType.lastIndexOf(">"))
                        : Object.class.getCanonicalName();
            }

            // 简单常用类型
            if (TypeUtils.isPrimitiveOrSimpleType(paramType)) {
                list.add(new KV(parameter.getParamName(), String.valueOf(TypeUtils.getExampleValue(paramType, true))));
                continue;
            }
            // 文件类型
            Set<String> fileParameterTypeSet = Stream.of("org.noear.solon.core.handle.UploadedFile").collect(Collectors.toSet());
            if (fileParameterTypeSet.contains(paramType)) {
                list.add(new KV(parameter.getParamName(), "file@[filepath]"));
                continue;
            }

            PsiClass psiClass = PsiClassHelper.findPsiClass(paramType, psiMethod.getProject());
            if (psiClass != null) {
                PsiField[] fields = psiClass.getAllFields();
                if (psiClass.isEnum()) {
                    list.add(new KV(parameter.getParamName(), fields.length > 1 ? fields[0].getName() : ""));
                    continue;
                }
                for (PsiField field : fields) {
                    if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                        continue;
                    }
                    PsiClass fieldClass = PsiClassHelper.findPsiClass(field.getType().getCanonicalText(), psiMethod.getProject());
                    if (fieldClass != null && fieldClass.isEnum()) {
                        PsiField[] enumFields = fieldClass.getAllFields();
                        list.add(new KV(field.getName(), enumFields.length > 1 ? enumFields[0].getName() : ""));
                    } else {
                        Object fieldDefaultValue = TypeUtils.getExampleValue(field.getType().getPresentableText(), true);
                        list.add(new KV(field.getName(), String.valueOf(fieldDefaultValue)));
                    }
                }
            }
        }
        return list;
    }

    /**
     * 构建RequestBody json 参数
     */
    public String buildRequestBodyJson(PsiMethod psiMethod) {
        return Arrays.stream(psiMethod.getParameterList().getParameters())
                     .filter(psiParameter -> psiParameter.hasAnnotation(REQUEST_BODY.getQualifiedName()))
                     .findFirst()
                     .map(psiParameter -> PsiClassHelper.convertClassToJSON(psiParameter.getType().getCanonicalText(), psiMethod.getProject()))
                     .orElse(null);
    }

    @NotNull
    private List<Parameter> getParameterList(PsiMethod psiMethod) {
        List<Parameter> parameterList = new ArrayList<>();

        Set<String> paramFilterTypes = getParamFilterTypes(psiMethod.getProject());

        PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
        for (PsiParameter psiParameter : psiParameters) {
            String paramTypeName = psiParameter.getType().getCanonicalText();
            if (paramFilterTypes.contains(paramTypeName)
                    || CollectionUtils.containsAny(paramFilterTypes, Arrays.stream(psiParameter.getAnnotations()).map(PsiAnnotation::getQualifiedName).collect(Collectors.toSet()))) {
                continue;
            }

            // @PathVariable
            PsiAnnotation pathVariableAnno = psiParameter.getAnnotation(PATH_VARIABLE.getQualifiedName());
            if (pathVariableAnno != null) {
                String paramName = ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(pathVariableAnno, "value"),
                        ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(pathVariableAnno, "name"), psiParameter.getName()
                        ));
                Parameter parameter = new Parameter(paramTypeName, paramName);
                parameterList.add(parameter);
                continue;
            }

            // @RequestParam
            PsiAnnotation requestParamAnno = psiParameter.getAnnotation(REQUEST_PARAM.getQualifiedName());
            if (requestParamAnno != null) {
                String paramName = ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(requestParamAnno, "value"),
                        ObjectUtils.defaultIfNull(PsiAnnotationHelper.getAnnotationValue(requestParamAnno, "name"), psiParameter.getName()
                        ));
                Parameter parameter = new Parameter(paramTypeName, paramName);
                parameterList.add(parameter);
                continue;
            }

            // 其他未包含指定注解
            Parameter parameter = new Parameter(paramTypeName, psiParameter.getName());
            parameterList.add(parameter);
        }
        return parameterList;
    }

    @NotNull
    private String getCombinedPath(@NotNull String typePath, @NotNull String methodPath) {
        if (typePath.isEmpty()) {
            typePath = "/";
        } else if (!typePath.startsWith("/")) {
            typePath = "/".concat(typePath);
        }

        if (!methodPath.isEmpty()) {
            if (!methodPath.startsWith("/") && !typePath.endsWith("/")) {
                methodPath = "/".concat(methodPath);
            }
        }

        return (typePath + methodPath).replace("//", "/");
    }

    @NotNull
    @Override
    public Set<String> getParamFilterTypes(@NotNull Project project) {
        return Stream.of(
                "java.util.Locale",
                "org.noear.solon.core.handle.Context",
                "org.noear.solon.core.handle.ModelAndView",
                "org.noear.solon.annotation.Header",
                "org.noear.solon.annotation.Cookie",
                "org.noear.solon.annotation.Body"
        ).collect(Collectors.toSet());
    }

    @Getter
    @AllArgsConstructor
    static class Parameter {
        private String paramType;
        private String paramName;
    }

    public static class SolonApiResolverProvider implements RestfulResolverProvider {
        @Override
        public @NotNull RequestResolver createRequestResolver() {
            return new SolonApiResolver();
        }
    }
}
