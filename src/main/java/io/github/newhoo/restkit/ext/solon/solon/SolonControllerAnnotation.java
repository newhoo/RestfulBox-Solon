package io.github.newhoo.restkit.ext.solon.solon;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SolonControllerAnnotation {

    CONTROLLER("Controller", "org.noear.solon.annotation.Controller");

    private final String shortName;
    private final String qualifiedName;
}