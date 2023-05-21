package io.github.newhoo.restkit.ext.solon.solon;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SolonRequestMethodAnnotation {

    REQUEST_MAPPING("Mapping", "org.noear.solon.annotation.Mapping", null),
    HEAD_MAPPING("Head", "org.noear.solon.annotation.Head", "Head"),
    GET_MAPPING("Get", "org.noear.solon.annotation.Get", "GET"),
    POST_MAPPING("Post", "org.noear.solon.annotation.Post", "POST"),
    PUT_MAPPING("Put", "org.noear.solon.annotation.Put", "PUT"),
    DELETE_MAPPING("Delete", "org.noear.solon.annotation.Delete", "DELETE"),
    PATCH_MAPPING("Patch", "org.noear.solon.annotation.Patch", "PATCH");

    private final String shortName;
    private final String qualifiedName;
    private final String method;
}