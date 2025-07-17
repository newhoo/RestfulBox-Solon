package io.github.newhoo.restkit.ext.solon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.newhoo.restkit.ext.solon.solon.SolonAnnotationHelper;
import io.github.newhoo.restkit.ext.solon.solon.SolonControllerAnnotation;
import io.github.newhoo.restkit.open.LanguageResolver;
import io.github.newhoo.restkit.open.ParamResolver;
import io.github.newhoo.restkit.open.RequestResolver;
import io.github.newhoo.restkit.open.ep.RestfulResolverProvider;
import io.github.newhoo.restkit.open.helper.java.JavaHelper;
import io.github.newhoo.restkit.open.helper.java.JavaTypeHelper;
import io.github.newhoo.restkit.open.model.KV;
import io.github.newhoo.restkit.open.model.ProjectSetting;
import io.github.newhoo.restkit.open.model.api.PsiRestItem;
import io.github.newhoo.restkit.open.model.api.RestItem;
import io.github.newhoo.restkit.open.model.api.SimpleLineMarkerInfo;
import io.github.newhoo.restkit.open.model.api.parameter.JsonStruct;
import io.github.newhoo.restkit.open.model.api.parameter.ParamType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestMethodAnnotation.REQUEST_MAPPING;
import static io.github.newhoo.restkit.ext.solon.solon.SolonRequestParamAnnotation.*;

/**
 * solon service scanner
 *
 * @since 1.0.0
 */
public class SolonApiResolver implements RequestResolver, ParamResolver<PsiMethod> {

    @NotNull
    @Override
    public String getFrameworkName() {
        return "Solon";
    }

    @Override
    public int order() {
        return 6;
    }

    @Override
    public @NotNull String getDescription() {
        return "- 支持 Solon 接口扫描和在线调试，识别 @Controller等注解<br/>- 支持 Java 语言";
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

                if (psiElement instanceof PsiClass psiClass) {
                    List<RestItem> serviceItemList = getRequestItemList(psiClass, module, JavaHelper.getLanguageResolver(psiElement));
                    itemList.addAll(serviceItemList);
                }
            }
        }
        return itemList;
    }

    @Override
    public boolean canNavigateToTree(@NotNull PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod psiMethod)) {
            return false;
        }
        if (!psiMethod.hasAnnotation(REQUEST_MAPPING.getQualifiedName())) {
            return false;
        }
        PsiClass containingClass = psiMethod.getContainingClass();
        return containingClass != null && containingClass.hasAnnotation(SolonControllerAnnotation.CONTROLLER.getQualifiedName());
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
        RestItem restItem = combineFirstRestItem(typeMethodPaths, methodMethodPaths, psiMethod, "");
        return restItem;
    }

    @Override
    public List<RestItem> tryGenerateRestItemsForPreview(@NotNull PsiElement psiElement) {
        PsiMethod psiMethod;
        if (psiElement instanceof PsiMethod) {
            psiMethod = (PsiMethod) psiElement;
        } else if (psiElement.getParent() instanceof PsiMethod) {
            psiMethod = (PsiMethod) psiElement.getParent();
        } else {
            return null;
        }
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null) {
            return getRequestItemListInScope(containingClass);
        }
        return null;
    }

    private List<RestItem> getRequestItemListInScope(PsiClass psiClass) {
        List<RestItem> itemList = new LinkedList<>();
        List<MethodPath> typeMethodPaths = SolonAnnotationHelper.getTypeMethodPaths(psiClass);
        LanguageResolver languageResolver = JavaHelper.getLanguageResolver(psiClass);
        for (PsiMethod psiMethod : psiClass.getMethods()) {
            List<MethodPath> methodMethodPaths = SolonAnnotationHelper.getMethodMethodPaths(psiMethod);

            RestItem restItem = combineFirstRestItem(typeMethodPaths, methodMethodPaths, psiMethod, "");
            if (restItem == null) {
                continue;
            }

            String apiName = languageResolver.findApiName(psiMethod).orElseGet(psiMethod::getName);
            restItem.setName(apiName);
            restItem.setFolderPath(psiClass.getName());
            itemList.add(restItem);
        }
        return itemList;
    }

    private List<RestItem> getRequestItemList(PsiClass psiClass, Module module, LanguageResolver languageResolver) {
        List<PsiMethod> psiMethods = new ArrayList<>(Arrays.asList(psiClass.getMethods()));
        for (PsiClass aSuper : psiClass.getSupers()) {
            if (!"java.lang.Object".equals(aSuper.getQualifiedName())) {
                psiMethods.addAll(Arrays.asList(aSuper.getMethods()));
            }
        }
        if (psiMethods.size() == 0) {
            return Collections.emptyList();
        }

        if (languageResolver.isIgnored(psiClass)) {
            return Collections.emptyList();
        }
        String groupName = languageResolver.findApiGroup(psiClass).orElseGet(psiClass::getQualifiedName);
        Set<String> classTags = languageResolver.findApiTags(psiClass);
        boolean showModuleFolder = ProjectSetting.getInstance(psiClass.getProject()).isShowModuleFolder();

        List<RestItem> itemList = new ArrayList<>();
        List<MethodPath> typeMethodPaths = SolonAnnotationHelper.getTypeMethodPaths(psiClass);

        for (PsiMethod psiMethod : psiMethods) {
            if (languageResolver.isIgnored(psiMethod)) {
                continue;
            }
            List<MethodPath> methodMethodPaths = SolonAnnotationHelper.getMethodMethodPaths(psiMethod);
            List<RestItem> restItems = combineTypeAndMethod(typeMethodPaths, methodMethodPaths, psiMethod, module);

            String apiName = languageResolver.findApiName(psiMethod).orElseGet(psiMethod::getName);
            Set<String> methodTags = languageResolver.findApiTags(psiMethod);
            for (RestItem item : restItems) {
                item.setName(apiName);
                item.setFolderPath(item.getModuleName(), groupName, showModuleFolder);
                item.setTags(new LinkedHashSet<String>(CollectionUtils.union(classTags, methodTags)));
            }

            itemList.addAll(restItems);
        }
        return itemList;
    }

    @NotNull
    @Override
    public List<KV> buildHeaders(@NotNull PsiMethod psiMethod) {
        List<KV> list = new ArrayList<>();
        PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
        for (PsiParameter psiParameter : psiParameters) {
            {
                PsiAnnotation requestHeaderAnno = psiParameter.getAnnotation(REQUEST_HEADER.getQualifiedName());
                if (requestHeaderAnno != null) {
                    String headerName = ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(requestHeaderAnno, "value"),
                            ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(requestHeaderAnno, "name"), psiParameter.getName()
                            ));
                    PsiClass fieldClass = JavaHelper.findPsiClass(psiParameter.getType().getCanonicalText(), psiMethod.getProject());
                    if (fieldClass != null && fieldClass.isEnum()) {
                        PsiField[] enumFields = fieldClass.getAllFields();
                        list.add(new KV(headerName, enumFields.length > 1 ? enumFields[0].getName() : ""));
                    } else {
                        Object fieldDefaultValue = JavaTypeHelper.getExampleValue(psiParameter.getType().getPresentableText(), psiParameter.getProject());
                        list.add(new KV(headerName, String.valueOf(fieldDefaultValue)));
                    }
                }
            }
            {
                PsiAnnotation requestHeaderAnno = psiParameter.getAnnotation(REQUEST_COOKIE.getQualifiedName());
                if (requestHeaderAnno != null) {
                    String headerName = ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(requestHeaderAnno, "value"),
                            ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(requestHeaderAnno, "name"), psiParameter.getName()
                            ));
                    Object fieldDefaultValue = JavaTypeHelper.getExampleValue(psiParameter.getType().getPresentableText(), psiParameter.getProject());
                    list.add(new KV("Cookie", headerName + "=" + fieldDefaultValue));
                }
            }
        }
        return list;
    }

    @NotNull
    @Override
    public List<KV> buildParams(@NotNull PsiMethod psiMethod) {
        return buildParamString(psiMethod);
    }

    @Override
    public @NotNull List<JsonStruct> buildParamStruct(@NotNull PsiMethod psiMethod) {
        return buildParamString(psiMethod).stream().map(kv -> new JsonStruct(kv)).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public String buildRequestBodyJson(@NotNull PsiMethod psiMethod) {
        return Arrays.stream(psiMethod.getParameterList().getParameters())
                     .filter(psiParameter -> psiParameter.hasAnnotation(REQUEST_BODY.getQualifiedName()))
                     .findFirst()
                     .map(psiParameter -> JavaHelper.convertClassToJSON(psiParameter.getType().getCanonicalText(), psiMethod.getProject()))
                     .orElse("");
    }

    @Override
    public JsonStruct buildRequestBodyStruct(@NotNull PsiMethod psiMethod) {
        return Arrays.stream(psiMethod.getParameterList().getParameters())
                     .filter(psiParameter -> psiParameter.hasAnnotation(REQUEST_BODY.getQualifiedName()))
                     .findFirst()
                     .map(psiParameter -> JavaHelper.convertClassToJSONStruct(psiParameter.getType().getCanonicalText(), psiMethod.getProject()))
                     .orElse(null);
    }

    @Override
    public @NotNull String buildResponseBodyJson(@NotNull PsiMethod psiMethod) {
        return Optional.ofNullable(psiMethod.getReturnType())
                       .flatMap(returnType -> JavaHelper.getLanguageResolver(psiMethod).findApiReturnType(psiMethod))
                       .map(returnType -> JavaHelper.convertClassToJSON(returnType, psiMethod.getProject()))
                       .orElse("");
    }

    @Override
    public JsonStruct buildResponseBodyStruct(@NotNull PsiMethod psiMethod) {
        return Optional.ofNullable(psiMethod.getReturnType())
                       .flatMap(returnType -> JavaHelper.getLanguageResolver(psiMethod).findApiReturnType(psiMethod))
                       .map(returnType -> JavaHelper.convertClassToJSONStruct(returnType, psiMethod.getProject()))
                       .orElse(null);
    }

    @Override
    public @NotNull String buildDescription(@NotNull PsiMethod psiMethod) {
        return JavaHelper.getLanguageResolver(psiMethod).findApiDescription(psiMethod).orElse("");
    }

    @NotNull
    public RestItem createRestServiceItem(@NotNull Module module, PsiMethod psiMethod, @NotNull String typePath, @NotNull String methodPath, String method) {
        String requestPath = getCombinedPath(typePath, methodPath);
        return new PsiRestItem<>(requestPath, method, module.getName(), psiMethod, this);
    }

    @NotNull
    public RestItem createRestServiceItem(@NotNull Module module, PsiMethod psiMethod, @NotNull String path, String method) {
        return new PsiRestItem<>(path, method, module.getName(), psiMethod, this);
    }

    public List<RestItem> combineTypeAndMethod(List<MethodPath> typeMethodPaths, List<MethodPath> methodMethodPaths, PsiMethod psiMethod, Module module) {
        List<RestItem> itemList = new ArrayList<>();
        for (MethodPath methodPath : methodMethodPaths) {
            if (typeMethodPaths.isEmpty()) {
                RestItem item = createRestServiceItem(module, psiMethod, "", methodPath.getPath(), methodPath.getMethod());
                itemList.add(item);
            } else {
                for (MethodPath typeMethodPath : typeMethodPaths) {
                    String combinedPath = getCombinedPath(typeMethodPath.getPath(), methodPath.getPath());
                    String typeMethod = typeMethodPath.getMethod();

                    if (typeMethod != null && !typeMethod.equals(methodPath.getMethod())) {
                        RestItem item = createRestServiceItem(module, psiMethod, combinedPath, typeMethod);
                        itemList.add(item);
                    }

                    RestItem item = createRestServiceItem(module, psiMethod, combinedPath, methodPath.getMethod());
                    itemList.add(item);
                }
            }
        }
        return itemList;
    }

    public RestItem combineFirstRestItem(List<MethodPath> typeMethodPaths, List<MethodPath> methodMethodPaths, PsiMethod psiMethod, String moduleName) {
        if (methodMethodPaths.isEmpty()) {
            return null;
        }
        MethodPath methodPath = methodMethodPaths.stream().filter(o -> StringUtils.isNotEmpty(o.getMethod())).findFirst().orElse(methodMethodPaths.get(0));
        if (typeMethodPaths.isEmpty()) {
            String requestPath = getCombinedPath("", methodPath.getPath());
            return new PsiRestItem<>(requestPath, methodPath.getMethod(), moduleName, psiMethod, this);
        } else {
            MethodPath typeMethodPath = typeMethodPaths.get(0);
            String combinedPath = getCombinedPath(typeMethodPath.getPath(), methodPath.getPath());
            String typeMethod = typeMethodPath.getMethod();

            if (typeMethod != null && !typeMethod.equals(methodPath.getMethod())) {
                return new PsiRestItem<>(combinedPath, typeMethod, moduleName, psiMethod, this);
            }

            return new PsiRestItem<>(combinedPath, methodPath.getMethod(), moduleName, psiMethod, this);
        }
    }

    public List<KV> buildParamString(PsiMethod psiMethod) {
        List<KV> list = new ArrayList<>();

        List<Parameter> parameterList = getParameterList(psiMethod);

        // 拼接参数
        for (Parameter parameter : parameterList) {
            String paramType = parameter.getParamType();

            // 数组|集合
            if (JavaTypeHelper.isArray(paramType) || JavaTypeHelper.isList(paramType)) {
                paramType = JavaTypeHelper.isArray(paramType)
                        ? paramType.replace("[]", "")
                        : paramType.contains("<")
                        ? paramType.substring(paramType.indexOf("<") + 1, paramType.lastIndexOf(">"))
                        : Object.class.getCanonicalName();
            }

            // 简单常用类型
            if (JavaTypeHelper.isPrimitiveOrSimpleType(paramType)) {
                list.add(new KV(parameter.getParamName(), String.valueOf(JavaTypeHelper.getExampleValue(paramType, psiMethod.getProject()))));
                continue;
            }
            // 文件类型
            Set<String> fileParameterTypeSet = Stream.of("org.noear.solon.core.handle.UploadedFile").collect(Collectors.toSet());
            if (fileParameterTypeSet.contains(paramType)) {
                list.add(new KV(parameter.getParamName(), ParamType.FILE_CUSTOM_DESCRIPTOR));
                continue;
            }

            PsiClass psiClass = JavaHelper.findPsiClass(paramType, psiMethod.getProject());
            if (psiClass != null) {
                PsiField[] fields = psiClass.getAllFields();
                if (psiClass.isEnum()) {
                    list.add(new KV(parameter.getParamName(), fields.length > 1 ? fields[0].getName() : ""));
                    continue;
                }
                for (PsiField field : fields) {
                    if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.TRANSIENT) || JavaHelper.getLanguageResolver(field).isIgnored(field)) {
                        continue;
                    }
                    PsiClass fieldClass = JavaHelper.findPsiClass(field.getType().getCanonicalText(), psiMethod.getProject());
                    if (fieldClass != null && fieldClass.isEnum()) {
                        PsiField[] enumFields = fieldClass.getAllFields();
                        list.add(new KV(field.getName(), enumFields.length > 1 ? enumFields[0].getName() : ""));
                    } else {
                        Object fieldDefaultValue = JavaTypeHelper.getExampleValue(field.getType().getPresentableText(), field.getProject());
                        list.add(new KV(field.getName(), String.valueOf(fieldDefaultValue)));
                    }
                }
            }
        }
        return list;
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
                String paramName = ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(pathVariableAnno, "value"),
                        ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(pathVariableAnno, "name"), psiParameter.getName()
                        ));
                Parameter parameter = new Parameter(paramTypeName, paramName);
                parameterList.add(parameter);
                continue;
            }

            // @RequestParam
            PsiAnnotation requestParamAnno = psiParameter.getAnnotation(REQUEST_PARAM.getQualifiedName());
            if (requestParamAnno != null) {
                String paramName = ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(requestParamAnno, "value"),
                        ObjectUtils.defaultIfNull(JavaHelper.getAnnotationValue(requestParamAnno, "name"), psiParameter.getName()
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
