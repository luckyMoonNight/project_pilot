package com.luckymoon.moon_readcode_server.analyzer.impl;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.luckymoon.moon_readcode_server.analyzer.JavaAstAnalyzer;
import com.luckymoon.moon_readcode_server.analyzer.dto.AnalyzeResult;
import com.luckymoon.moon_readcode_server.entity.CodeClass;
import com.luckymoon.moon_readcode_server.entity.CodeFile;
import com.luckymoon.moon_readcode_server.entity.CodeMethod;
import com.luckymoon.moon_readcode_server.entity.CodeRelation;
import com.luckymoon.moon_readcode_server.mapper.CodeClassMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeFileMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeMethodMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认的 JavaParser 实现。
 *
 * 处理流程（per project）：
 *   1. 删除该 project 旧的 class/method/relation 数据，避免脏数据；
 *   2. 遍历 code_file 表中所有 java 文件；
 *   3. 用 JavaParser 解析为 AST，抽取 class/method 入库；
 *   4. 二次遍历方法体，识别方法调用、字段注入、继承等关系。
 *
 * 注意：当前实现使用基于"类名"的弱解析（不开 SymbolSolver），跨工程跳转和重载消歧不完美，
 * 但对单工程问答场景已经够用，且无须配置 classpath。后续可平滑升级到 SymbolSolver。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaAstAnalyzerImpl implements JavaAstAnalyzer {

    private final CodeFileMapper codeFileMapper;
    private final CodeClassMapper codeClassMapper;
    private final CodeMethodMapper codeMethodMapper;
    private final CodeRelationMapper codeRelationMapper;

    private final JavaParser javaParser = new JavaParser();

    @Override
    @Transactional
    public AnalyzeResult analyze(String projectId) {
        long start = System.currentTimeMillis();

        // 简化策略：每次重新分析都先清空，避免增量带来的孤儿数据
        codeRelationMapper.deleteByProjectId(projectId);
        codeMethodMapper.deleteByProjectId(projectId);
        codeClassMapper.deleteByProjectId(projectId);

        List<CodeFile> files = codeFileMapper.selectByProjectId(projectId);
        Counter counter = new Counter();
        counter.totalFiles = files.size();

        // 待落地的关系，先收集再批量插入，提高效率
        List<CodeRelation> pendingRelations = new ArrayList<>();

        for (CodeFile file : files) {
            if (!"java".equalsIgnoreCase(file.getFileType())) {
                continue;
            }
            try {
                parseAndPersist(file, pendingRelations, counter);
                counter.parsedFiles++;
            } catch (Exception e) {
                log.warn("AST 解析失败 file={} : {}", file.getFilePath(), e.getMessage());
                counter.failedFiles++;
            }
        }

        if (!pendingRelations.isEmpty()) {
            // 分批插入，避免单条 SQL 过大
            int batchSize = 500;
            for (int i = 0; i < pendingRelations.size(); i += batchSize) {
                List<CodeRelation> sub = pendingRelations.subList(
                        i, Math.min(i + batchSize, pendingRelations.size()));
                codeRelationMapper.batchInsert(sub);
            }
        }
        counter.relations = pendingRelations.size();

        long cost = System.currentTimeMillis() - start;
        log.info("AST 分析完成 projectId={} 文件={}/{} 类={} 方法={} 关系={} 耗时={}ms",
                projectId, counter.parsedFiles, counter.totalFiles,
                counter.classes, counter.methods, counter.relations, cost);

        return AnalyzeResult.builder()
                .projectId(projectId)
                .totalFiles(counter.totalFiles)
                .parsedFiles(counter.parsedFiles)
                .failedFiles(counter.failedFiles)
                .classes(counter.classes)
                .methods(counter.methods)
                .relations(counter.relations)
                .costMillis(cost)
                .build();
    }

    private void parseAndPersist(CodeFile file,
                                 List<CodeRelation> pendingRelations,
                                 Counter counter) {
        ParseResult<CompilationUnit> parseResult = javaParser.parse(file.getContent());
        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            throw new IllegalStateException("JavaParser 解析失败：" + parseResult.getProblems());
        }
        CompilationUnit cu = parseResult.getResult().get();
        String packageName = cu.getPackageDeclaration()
                .map(p -> p.getName().asString())
                .orElse(file.getPackageName());

        // 顶层类型与所有嵌套类型一起处理；用 findAll 拿全所有 TypeDeclaration。
        // 注意：findAll(TypeDeclaration.class) 返回 List<TypeDeclaration<capture#?>>，无法直接迭代为 TypeDeclaration<?>，
        // 通过原始类型集合 + cast 显式窄化绕开泛型捕获问题。
        @SuppressWarnings("rawtypes")
        List<TypeDeclaration> rawTypes = cu.findAll(TypeDeclaration.class);
        for (TypeDeclaration<?> type : rawTypes) {
            handleType(file, type, packageName, pendingRelations, counter);
        }
    }

    private void handleType(CodeFile file, TypeDeclaration<?> type, String packageName,
                            List<CodeRelation> pendingRelations, Counter counter) {
        String qualifiedName = computeQualifiedName(type, packageName);
        CodeClass codeClass = persistClass(file, type, packageName, qualifiedName);
        counter.classes++;
        // 类级关系：继承、实现、字段注入、字段类型依赖
        collectInheritanceRelations(codeClass, type, pendingRelations);
        collectFieldRelations(codeClass, type, pendingRelations);
        // 解析方法和构造方法（统一作为 callable 处理）
        for (CallableDeclaration<?> callable : collectCallables(type)) {
            CodeMethod codeMethod = persistCallable(codeClass, callable, file.getProjectId());
            counter.methods++;
            collectMethodCallRelations(codeMethod, callable, pendingRelations);
        }
    }

    /**
     * 计算类型的全限定名，支持嵌套类型——通过向上回溯父级 TypeDeclaration 拼接。
     */
    private String computeQualifiedName(TypeDeclaration<?> type, String packageName) {
        // 从内到外拼接所有外层类型名
        List<String> names = new ArrayList<>();
        names.add(type.getNameAsString());
        Node parent = type.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof TypeDeclaration<?> td) {
                names.add(0, td.getNameAsString());
            }
            parent = parent.getParentNode().orElse(null);
        }
        String joined = String.join(".", names);
        return packageName == null || packageName.isBlank() ? joined : packageName + "." + joined;
    }

    /**
     * 收集一个类型下需要持久化的所有 callable（普通方法 + 构造方法）。
     * 注意：仅取本类型直接声明的，不递归到嵌套类型——嵌套类型在 findAll 阶段已独立处理过。
     */
    private List<CallableDeclaration<?>> collectCallables(TypeDeclaration<?> type) {
        List<CallableDeclaration<?>> result = new ArrayList<>();
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof MethodDeclaration md) {
                result.add(md);
            } else if (member instanceof ConstructorDeclaration cd) {
                result.add(cd);
            }
        }
        return result;
    }

    /* ---------------------------------------------------------------- */
    /*                       类元数据持久化                              */
    /* ---------------------------------------------------------------- */

    private CodeClass persistClass(CodeFile file, TypeDeclaration<?> type,
                                   String packageName, String qualifiedName) {
        CodeClass codeClass = new CodeClass();
        codeClass.setProjectId(file.getProjectId());
        codeClass.setFileId(file.getId());
        codeClass.setClassName(type.getNameAsString());
        codeClass.setQualifiedName(qualifiedName);
        codeClass.setPackageName(packageName);
        codeClass.setClassType(resolveClassType(type));
        codeClass.setStereotype(resolveStereotype(type));
        codeClass.setSuperClass(resolveSuperClass(type));
        codeClass.setInterfaces(resolveInterfaces(type));
        codeClass.setAnnotations(resolveAnnotations(type.getAnnotations()));
        codeClass.setStartLine(type.getBegin().map(p -> p.line).orElse(null));
        codeClass.setEndLine(type.getEnd().map(p -> p.line).orElse(null));
        codeClassMapper.insert(codeClass);
        return codeClass;
    }

    private String resolveClassType(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration coi) {
            return coi.isInterface() ? "INTERFACE" : "CLASS";
        }
        if (type instanceof EnumDeclaration) {
            return "ENUM";
        }
        if (type instanceof RecordDeclaration) {
            return "RECORD";
        }
        if (type instanceof AnnotationDeclaration) {
            return "ANNOTATION";
        }
        return "CLASS";
    }

    /**
     * 根据类注解推断 Spring 业务原型。
     * 这里只看简单注解名，不解析全限定名，覆盖率 95% 以上的 Spring 项目。
     */
    private String resolveStereotype(TypeDeclaration<?> type) {
        for (AnnotationExpr a : type.getAnnotations()) {
            String name = a.getNameAsString();
            switch (name) {
                case "RestController", "Controller" -> { return "CONTROLLER"; }
                case "Service" -> { return "SERVICE"; }
                case "Repository", "Mapper" -> { return "MAPPER"; }
                case "Entity", "Table" -> { return "ENTITY"; }
                case "Configuration", "ConfigurationProperties" -> { return "CONFIG"; }
                case "Component" -> { return "COMPONENT"; }
                default -> { /* continue */ }
            }
        }
        return "OTHER";
    }

    private String resolveSuperClass(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration coi && !coi.getExtendedTypes().isEmpty()) {
            return coi.getExtendedTypes().get(0).getNameAsString();
        }
        return null;
    }

    private String resolveInterfaces(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration coi) {
            List<ClassOrInterfaceType> impls = coi.isInterface()
                    ? coi.getExtendedTypes()
                    : coi.getImplementedTypes();
            if (impls.isEmpty()) {
                return null;
            }
            return impls.stream().map(ClassOrInterfaceType::getNameAsString)
                    .collect(Collectors.joining(","));
        }
        return null;
    }

    private String resolveAnnotations(List<AnnotationExpr> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return null;
        }
        return annotations.stream()
                .map(a -> "@" + a.getNameAsString())
                .collect(Collectors.joining(","));
    }

    /* ---------------------------------------------------------------- */
    /*                       方法元数据持久化                            */
    /* ---------------------------------------------------------------- */

    /**
     * 持久化一个 callable（方法或构造器）。
     * 构造器的 returnType 标记为 "<init>"，methodName 使用类的简单名，与 Java 字节码语义对齐。
     */
    private CodeMethod persistCallable(CodeClass codeClass, CallableDeclaration<?> callable, String projectId) {
        boolean isConstructor = callable instanceof ConstructorDeclaration;

        NodeList<Parameter> params = callable.getParameters();
        String paramTypes = params.stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        String methodName = isConstructor ? codeClass.getClassName() : callable.getNameAsString();
        String signature = codeClass.getQualifiedName() + "#" + methodName + "(" + paramTypes + ")";

        // 参数 JSON：手工拼，避免引入额外序列化器
        StringBuilder paramJson = new StringBuilder("[");
        for (int i = 0; i < params.size(); i++) {
            Parameter p = params.get(i);
            if (i > 0) paramJson.append(",");
            paramJson.append("{\"name\":\"")
                    .append(escapeJson(p.getNameAsString()))
                    .append("\",\"type\":\"")
                    .append(escapeJson(p.getType().asString()))
                    .append("\"}");
        }
        paramJson.append("]");

        String returnType;
        if (callable instanceof MethodDeclaration md) {
            returnType = md.getType().asString();
        } else {
            returnType = "<init>";
        }

        CodeMethod method = new CodeMethod();
        method.setProjectId(projectId);
        method.setClassId(codeClass.getId());
        method.setMethodName(methodName);
        method.setSignature(signature);
        method.setReturnType(returnType);
        method.setParameters(paramJson.toString());
        method.setAnnotations(resolveAnnotations(callable.getAnnotations()));
        method.setModifiers(callable.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .collect(Collectors.joining(",")));
        method.setBodySnippet(callable.toString());
        method.setStartLine(callable.getBegin().map(p -> p.line).orElse(null));
        method.setEndLine(callable.getEnd().map(p -> p.line).orElse(null));
        codeMethodMapper.insert(method);
        return method;
    }

    /* ---------------------------------------------------------------- */
    /*                          关系收集                                 */
    /* ---------------------------------------------------------------- */

    private void collectInheritanceRelations(CodeClass codeClass, TypeDeclaration<?> type,
                                             List<CodeRelation> pendingRelations) {
        if (!(type instanceof ClassOrInterfaceDeclaration coi)) {
            return;
        }
        for (ClassOrInterfaceType ext : coi.getExtendedTypes()) {
            pendingRelations.add(buildRelation(codeClass.getProjectId(), "EXTEND",
                    codeClass.getId(), "CLASS", ext.getNameAsString()));
        }
        for (ClassOrInterfaceType impl : coi.getImplementedTypes()) {
            pendingRelations.add(buildRelation(codeClass.getProjectId(), "IMPLEMENT",
                    codeClass.getId(), "CLASS", impl.getNameAsString()));
        }
    }

    /**
     * 收集方法体内的方法调用作为 METHOD_CALL 关系。
     * 弱解析阶段：to_ref 记录"被调用方法名"或"scope.方法名"，后续可在 Phase 2.5 做 ref 解析回填 to_id。
     */
    private void collectMethodCallRelations(CodeMethod method, CallableDeclaration<?> callable,
                                            List<CodeRelation> pendingRelations) {
        callable.findAll(MethodCallExpr.class).forEach(call -> {
            String ref = call.getScope()
                    .map(s -> s.toString() + "." + call.getNameAsString())
                    .orElse(call.getNameAsString());
            pendingRelations.add(buildRelation(method.getProjectId(), "METHOD_CALL",
                    method.getId(), "METHOD", ref));
        });
    }

    /**
     * 收集字段相关的关系：
     *   - FIELD_INJECT：被 @Autowired / @Resource 标记，或被 final + 构造注入风格使用的字段类型；
     *   - CLASS_DEPEND：其他普通字段的类型依赖。
     * 同时去重，避免一个类有多个相同类型字段时产生重复 CLASS_DEPEND 行。
     */
    private void collectFieldRelations(CodeClass codeClass, TypeDeclaration<?> type,
                                       List<CodeRelation> pendingRelations) {
        Set<String> seenInject = new HashSet<>();
        Set<String> seenDepend = new HashSet<>();

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof FieldDeclaration field)) {
                continue;
            }
            boolean inject = isInjected(field);
            for (VariableDeclarator var : field.getVariables()) {
                String typeName = simpleTypeName(var.getType().asString());
                if (typeName == null || isPrimitiveOrCommon(typeName)) {
                    continue;
                }
                if (inject) {
                    if (seenInject.add(typeName)) {
                        pendingRelations.add(buildRelation(codeClass.getProjectId(),
                                "FIELD_INJECT", codeClass.getId(), "CLASS", typeName));
                    }
                } else {
                    if (seenDepend.add(typeName)) {
                        pendingRelations.add(buildRelation(codeClass.getProjectId(),
                                "CLASS_DEPEND", codeClass.getId(), "CLASS", typeName));
                    }
                }
            }
        }
    }

    /** 字段是否为依赖注入：@Autowired / @Resource / @Inject，或类被 @RequiredArgsConstructor 标注且字段为 final */
    private boolean isInjected(FieldDeclaration field) {
        for (AnnotationExpr a : field.getAnnotations()) {
            String name = a.getNameAsString();
            if ("Autowired".equals(name) || "Resource".equals(name) || "Inject".equals(name)) {
                return true;
            }
        }
        // 被 @RequiredArgsConstructor / @AllArgsConstructor 标注的类，其 final 字段相当于构造注入
        if (field.isFinal() && field.getParentNode().isPresent()
                && field.getParentNode().get() instanceof TypeDeclaration<?> owner) {
            for (AnnotationExpr a : owner.getAnnotations()) {
                String name = a.getNameAsString();
                if ("RequiredArgsConstructor".equals(name) || "AllArgsConstructor".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 取类型的简单名（剥掉泛型部分），如 List<User> -> List */
    private String simpleTypeName(String typeAsString) {
        if (typeAsString == null) return null;
        int lt = typeAsString.indexOf('<');
        String base = lt < 0 ? typeAsString : typeAsString.substring(0, lt);
        return base.trim();
    }

    /** 过滤掉基本类型和 java.lang 中的常见类型，避免噪音关系 */
    private boolean isPrimitiveOrCommon(String typeName) {
        return switch (typeName) {
            case "void", "boolean", "byte", "short", "int", "long", "float", "double", "char",
                 "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character",
                 "String", "Object", "Number", "CharSequence",
                 "List", "Map", "Set", "Collection", "Optional",
                 "LocalDate", "LocalDateTime", "LocalTime", "Instant", "Date", "BigDecimal", "BigInteger" -> true;
            default -> false;
        };
    }

    private CodeRelation buildRelation(String projectId, String type, Long fromId,
                                       String fromType, String toRef) {
        CodeRelation rel = new CodeRelation();
        rel.setProjectId(projectId);
        rel.setRelationType(type);
        rel.setFromId(fromId);
        rel.setFromType(fromType);
        rel.setToRef(toRef);
        return rel;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 内部计数器 */
    private static class Counter {
        int totalFiles;
        int parsedFiles;
        int failedFiles;
        int classes;
        int methods;
        int relations;
    }
}
