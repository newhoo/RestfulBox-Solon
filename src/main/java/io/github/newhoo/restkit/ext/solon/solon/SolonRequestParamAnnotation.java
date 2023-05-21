package io.github.newhoo.restkit.ext.solon.solon;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SolonRequestParamAnnotation {
    REQUEST_PARAM("Param", "org.noear.solon.annotation.Param"),
    REQUEST_BODY("Body", "org.noear.solon.annotation.Body"),
    PATH_VARIABLE("Path", "org.noear.solon.annotation.Path"),
    REQUEST_HEADER("Header", "org.noear.solon.annotation.Header"),
    REQUEST_COOKIE("Cookie", "org.noear.solon.annotation.Cookie");

    private final String shortName;
    private final String qualifiedName;
}