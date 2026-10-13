package com.lldpractice.lld_practice_platform.model;

import java.util.List;

public record ClassNode(String name, ClassKind kind, String summary, List<String> extendsTypes,
                        List<String> implementsTypes, List<FieldNode> fields, List<MethodNode> methods,
                        SourceRef source, String signatureHash, String bodyHash, List<String> enumConstants) {
    public ClassNode {
        extendsTypes = List.copyOf(extendsTypes);
        implementsTypes = List.copyOf(implementsTypes);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
        enumConstants = enumConstants == null ? List.of() : List.copyOf(enumConstants);
    }

    public ClassNode(String name, ClassKind kind, String summary, List<String> extendsTypes,
                     List<String> implementsTypes, List<FieldNode> fields, List<MethodNode> methods,
                     SourceRef source, String signatureHash, String bodyHash) {
        this(name, kind, summary, extendsTypes, implementsTypes, fields, methods, source, signatureHash, bodyHash,
                List.of());
    }

    public boolean isInterface() {
        return kind == ClassKind.INTERFACE;
    }
}
