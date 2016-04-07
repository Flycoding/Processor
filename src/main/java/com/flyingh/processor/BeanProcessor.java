package com.flyingh.processor;

import com.github.javaparser.ASTHelper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Created by Flycoding on 2016/4/7.
 */
@SupportedAnnotationTypes("com.flyingh.annotation.Bean")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BeanProcessor extends AbstractProcessor {

    private Pattern pattern;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        pattern = Pattern.compile("[a-z]+|[a-z]+(?=[A-Z])|[A-Z][a-z]*");
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final Messager messager = processingEnv.getMessager();
        final Filer filer = processingEnv.getFiler();
        annotations.stream().forEach(annotation ->
                roundEnv.getElementsAnnotatedWith(annotation)
                        .forEach(o -> {
                            final List<Element> list = o.getEnclosedElements().stream()
                                    .filter(element -> element.getKind() == ElementKind.FIELD)
                                    .filter(element -> !element.getModifiers().contains(Modifier.STATIC))
                                    .collect(toList());
                            final TypeElement te = (TypeElement) o;
                            final PackageElement pe = (PackageElement) te.getEnclosingElement();
                            try {
                                final Path path = Paths.get("src", "main", "java", te.getQualifiedName().toString().replace(".", File.separator) + ".java");
                                final CompilationUnit compilationUnit = JavaParser.parse(path.toFile());
                                final TypeDeclaration type = compilationUnit.getTypes().get(0);
                                final List<BodyDeclaration> members = type.getMembers();
                                final List<FieldDeclaration> toRemoveList = members.stream().filter(FieldDeclaration.class::isInstance).map(FieldDeclaration.class::cast)
                                        .filter(fieldDeclaration -> (fieldDeclaration.getModifiers() & ModifierSet.STATIC) != 0)
                                        .collect(toList());
                                members.removeAll(toRemoveList);
                                list.forEach(element -> {
                                    final String name = getConstantName(element);
                                    ASTHelper.addMember(type, new FieldDeclaration(
                                            ModifierSet.PUBLIC + ModifierSet.STATIC + ModifierSet.FINAL,
                                            new ClassOrInterfaceType("String"),
                                            new VariableDeclarator(
                                                    new VariableDeclaratorId(
                                                            name.toUpperCase()),
                                                    new NameExpr("\"" + element.getSimpleName() + "\"")
                                            )
                                    ));
                                });
                                Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
                            } catch (final ParseException e) {
                                e.printStackTrace();
                            } catch (final IOException e) {
                                e.printStackTrace();
                            }
//                            try {
//                                final JavaFileObject javaFileObject = filer.createSourceFile(te.getSimpleName() + "Info");
//                                try (BufferedWriter writer = new BufferedWriter(javaFileObject.openWriter())) {
//                                    writer.append("package ")
//                                            .append(pe.getQualifiedName())
//                                            .append(";");
//                                    writer.newLine();
//                                    writer.newLine();
//                                    writer.append("public class ").append(te.getSimpleName()).append("Info {");
//                                    writer.newLine();
//                                    writer.newLine();
//                                    list.forEach(element -> {
//                                        try {
//                                            writer.append("\tpublic static final String")
//                                                    .append(" ")
//                                                    .append(element.getSimpleName().toString().toUpperCase())
//                                                    .append(" = \"").append(element.getSimpleName()).append("\"").append(";");
//                                            writer.newLine();
//                                            writer.newLine();
//                                        } catch (final IOException e) {
//                                            e.printStackTrace();
//                                        }
//                                    });
//                                    writer.append("}");
//                                    messager.printMessage(Diagnostic.Kind.NOTE, writer.toString());
//                                }
//                            } catch (final IOException e) {
//                                e.printStackTrace();
//                            }
                        }));
        return true;
    }

    private String getConstantName(final Element element) {
        final String name = element.getSimpleName().toString();
        final Matcher matcher = pattern.matcher(name);
        final List<String> list = new ArrayList<>();
        while (matcher.find()) {
            list.add(matcher.group());
        }
        return list.stream().collect(joining("_")).toUpperCase();
    }
}
