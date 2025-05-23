package org.checkerframework.framework.stub;

import com.github.javaparser.ParseException;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.StubUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.ReceiverParameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

import org.checkerframework.afu.scenelib.annotations.Annotation;
import org.checkerframework.afu.scenelib.annotations.el.AClass;
import org.checkerframework.afu.scenelib.annotations.el.ADeclaration;
import org.checkerframework.afu.scenelib.annotations.el.AElement;
import org.checkerframework.afu.scenelib.annotations.el.AField;
import org.checkerframework.afu.scenelib.annotations.el.AMethod;
import org.checkerframework.afu.scenelib.annotations.el.AScene;
import org.checkerframework.afu.scenelib.annotations.el.ATypeElement;
import org.checkerframework.afu.scenelib.annotations.el.AnnotationDef;
import org.checkerframework.afu.scenelib.annotations.el.BoundLocation;
import org.checkerframework.afu.scenelib.annotations.el.DefException;
import org.checkerframework.afu.scenelib.annotations.el.LocalLocation;
import org.checkerframework.afu.scenelib.annotations.el.TypePathEntry;
import org.checkerframework.afu.scenelib.annotations.io.IndexFileParser;
import org.checkerframework.afu.scenelib.annotations.io.IndexFileWriter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.ClassGetName;
import org.checkerframework.checker.signature.qual.DotSeparatedIdentifiers;
import org.checkerframework.checker.signature.qual.FullyQualifiedName;
import org.checkerframework.framework.util.JavaParserUtil;
import org.checkerframework.javacutil.BugInCF;
import org.plumelib.reflection.Signatures;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convert a JAIF file plus a stub file into index files (JAIFs). Note that the resulting index
 * files will not include annotation definitions, for which stubfiles do not generally provide
 * complete information.
 *
 * <p>An instance of the class represents conversion of 1 stub file, but the static {@link
 * #main(String[])} method converts multiple stub files, instantiating the class multiple times.
 */
public class ToIndexFileConverter extends GenericVisitorAdapter<Void, AElement> {
    // The possessive modifiers "*+" are for efficiency only.
    // private static Pattern packagePattern =
    //         Pattern.compile("\\bpackage *+((?:[^.]*+[.] *+)*+[^ ]*) *+;");
    /** A pattern that matches an import statement. */
    private static final Pattern importPattern =
            Pattern.compile("\\bimport *+((?:[^.]*+[.] *+)*+[^ ]*) *+;");

    /**
     * Package name that is active at the current point in the input file. Changes as package
     * declarations are encountered.
     */
    private final @DotSeparatedIdentifiers String pkgName;

    /** Imports that appear in the stub file. */
    private final List<String> imports;

    /** A scene read from the input JAIF file, and will be written to the output JAIF file. */
    private final AScene scene;

    /**
     * Creates a new ToIndexFileConverter.
     *
     * @param pkgDecl the AST node for package declaration
     * @param importDecls the AST nodes for import declarations
     * @param scene scene for visitor methods to fill in
     */
    @SuppressWarnings("signature") // https://tinyurl.com/cfissue/658 for getNameAsString
    public ToIndexFileConverter(
            @Nullable PackageDeclaration pkgDecl,
            List<ImportDeclaration> importDecls,
            AScene scene) {
        this.scene = scene;
        pkgName = pkgDecl == null ? null : pkgDecl.getNameAsString();
        if (importDecls == null) {
            imports = Collections.emptyList();
        } else {
            ArrayList<String> imps = new ArrayList<>(importDecls.size());
            for (ImportDeclaration decl : importDecls) {
                if (!decl.isStatic()) {
                    Matcher m = importPattern.matcher(decl.toString());
                    if (m.find()) {
                        String s = m.group(1);
                        if (s != null) {
                            imps.add(s);
                        }
                    }
                }
            }
            imps.trimToSize();
            imports = Collections.unmodifiableList(imps);
        }
    }

    /**
     * Parse stub files and write out equivalent JAIFs. Note that the results do not include
     * annotation definitions, for which stubfiles do not generally provide complete information.
     *
     * @param args name of JAIF with annotation definition, followed by names of stub files to be
     *     converted (if none given, program reads from standard input)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: java ToIndexFileConverter myfile.jaif [stubfile...]");
            System.err.println("(myfile.jaif contains needed annotation definitions)");
            System.exit(1);
        }

        AScene scene = new AScene();
        try {
            // args[0] is a jaif file with needed annotation definitions
            IndexFileParser.parseFile(args[0], scene);

            if (args.length == 1) {
                convert(scene, System.in, System.out);
                return;
            }

            for (int i = 1; i < args.length; i++) {
                String f0 = args[i];
                String f1 =
                        (f0.endsWith(".astub") ? f0.substring(0, f0.length() - 6) : f0) + ".jaif";
                try (InputStream in = new BufferedInputStream(new FileInputStream(f0));
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(f1)); ) {
                    convert(new AScene(scene), in, out);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Augment given scene with information from stubfile, reading stubs from input stream and
     * writing JAIF to output stream.
     *
     * @param scene the initial scene
     * @param in stubfile contents
     * @param out the output stream for the JAIF file that holds the augmented scene
     * @throws ParseException if the stub file cannot be parsed
     * @throws DefException if two different definitions of the same annotation cannot be unified
     * @throws IOException if there is trouble with file reading or writing
     */
    private static void convert(AScene scene, InputStream in, OutputStream out)
            throws IOException, DefException, ParseException {
        StubUnit iu;
        try {
            iu = JavaParserUtil.parseStubUnit(in);
        } catch (ParseProblemException e) {
            iu = null;
            throw new BugInCF(
                    "ToIndexFileConverter: exception from JavaParser.parseStubUnit for InputStream."
                            + System.lineSeparator()
                            + "Problem message with problems encountered: "
                            + e.getMessage());
        }
        extractScene(iu, scene);
        try (Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            IndexFileWriter.write(scene, w);
        }
    }

    /**
     * Entry point of recursive-descent IndexUnit to AScene transformer. It operates by visiting the
     * stub and scene in parallel, descending into them in the same way. It augments the existing
     * scene (it does not create a new scene).
     *
     * @param iu {@link StubUnit} representing stubfile
     */
    private static void extractScene(StubUnit iu, AScene scene) {
        for (CompilationUnit cu : iu.getCompilationUnits()) {
            NodeList<TypeDeclaration<?>> typeDecls = cu.getTypes();
            if (typeDecls != null && cu.getPackageDeclaration().isPresent()) {
                List<ImportDeclaration> impDecls = cu.getImports();
                PackageDeclaration pkgDecl = cu.getPackageDeclaration().get();
                for (TypeDeclaration<?> typeDecl : typeDecls) {
                    ToIndexFileConverter converter =
                            new ToIndexFileConverter(pkgDecl, impDecls, scene);
                    String pkgName = converter.pkgName;
                    String name = typeDecl.getNameAsString();
                    if (pkgName != null) {
                        name = pkgName + "." + name;
                    }
                    typeDecl.accept(converter, scene.classes.getVivify(name));
                }
            }
        }
    }

    /**
     * Builds simplified annotation from its declaration. Only the name is included, because
     * stubfiles do not generally have access to the full definitions of annotations.
     */
    private static @Nullable Annotation extractAnnotation(AnnotationExpr expr) {
        String exprName = expr.toString().substring(1); // leave off leading '@'

        // Eliminate jdk.Profile+Annotation, a synthetic annotation that
        // the JDK adds, apparently for profiling.
        if (exprName.contains("+")) {
            return null;
        }
        @SuppressWarnings("signature") // special case for annotations containing "+"
        AnnotationDef def =
                new AnnotationDef(exprName, "ToIndexFileConverter.extractAnnotation(" + expr + ")");
        def.setFieldTypes(Collections.emptyMap());
        return new Annotation(def, Collections.emptyMap());
    }

    @Override
    public Void visit(AnnotationDeclaration decl, AElement elem) {
        return null;
    }

    @Override
    public Void visit(BlockStmt stmt, AElement elem) {
        return null;
        // super.visit(stmt, elem);
    }

    @Override
    public Void visit(ClassOrInterfaceDeclaration decl, AElement elem) {
        visitDecl(decl, (ADeclaration) elem);
        return super.visit(decl, elem);
    }

    @Override
    public Void visit(ConstructorDeclaration decl, AElement elem) {
        List<Parameter> params = decl.getParameters();
        List<AnnotationExpr> rcvrAnnos = decl.getAnnotations();
        BlockStmt body = decl.getBody();
        StringBuilder sb = new StringBuilder("<init>(");
        AClass clazz = (AClass) elem;
        AMethod method;

        // Some of the methods in the generated parser use null to represent an empty list.
        if (params != null) {
            for (Parameter param : params) {
                Type ptype = param.getType();
                sb.append(getJVML(ptype));
            }
        }
        sb.append(")V");
        method = clazz.methods.getVivify(sb.toString());
        visitDecl(decl, method);
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                Parameter param = params.get(i);
                AField field = method.parameters.getVivify(i);
                visitType(param.getType(), field.type);
            }
        }
        if (rcvrAnnos != null) {
            for (AnnotationExpr expr : rcvrAnnos) {
                Annotation anno = extractAnnotation(expr);
                method.receiver.tlAnnotationsHere.add(anno);
            }
        }
        return body == null ? null : body.accept(this, method);
        // return super.visit(decl, elem);
    }

    @Override
    public Void visit(EnumConstantDeclaration decl, AElement elem) {
        AField field = ((AClass) elem).fields.getVivify(decl.getNameAsString());
        visitDecl(decl, field);
        return super.visit(decl, field);
    }

    @Override
    public Void visit(EnumDeclaration decl, AElement elem) {
        visitDecl(decl, (ADeclaration) elem);
        return super.visit(decl, elem);
    }

    @Override
    public Void visit(FieldDeclaration decl, AElement elem) {
        for (VariableDeclarator v : decl.getVariables()) {
            AClass clazz = (AClass) elem;
            AField field = clazz.fields.getVivify(v.getNameAsString());
            visitDecl(decl, field);
            visitType(decl.getCommonType(), field.type);
        }
        return null;
    }

    @Override
    public Void visit(InitializerDeclaration decl, AElement elem) {
        BlockStmt block = decl.getBody();
        AClass clazz = (AClass) elem;
        block.accept(this, clazz.methods.getVivify(decl.isStatic() ? "<clinit>" : "<init>"));
        return null;
    }

    @Override
    public Void visit(MethodDeclaration decl, AElement elem) {
        Type type = decl.getType();
        List<Parameter> params = decl.getParameters();
        List<TypeParameter> typeParams = decl.getTypeParameters();
        Optional<ReceiverParameter> rcvrParam = decl.getReceiverParameter();
        BlockStmt body = decl.getBody().orElse(null);
        StringBuilder sb = new StringBuilder(decl.getNameAsString()).append('(');
        AClass clazz = (AClass) elem;
        AMethod method;
        if (params != null) {
            for (Parameter param : params) {
                Type ptype = param.getType();
                sb.append(getJVML(ptype));
            }
        }
        sb.append(')').append(getJVML(type));
        method = clazz.methods.getVivify(sb.toString());
        visitDecl(decl, method);
        visitType(type, method.returnType);
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                Parameter param = params.get(i);
                AField field = method.parameters.getVivify(i);
                visitType(param.getType(), field.type);
            }
        }
        if (rcvrParam.isPresent()) {
            for (AnnotationExpr expr : rcvrParam.get().getAnnotations()) {
                Annotation anno = extractAnnotation(expr);
                method.receiver.type.tlAnnotationsHere.add(anno);
            }
        }
        if (typeParams != null) {
            for (int i = 0; i < typeParams.size(); i++) {
                TypeParameter typeParam = typeParams.get(i);
                List<ClassOrInterfaceType> bounds = typeParam.getTypeBound();
                if (bounds != null) {
                    for (int j = 0; j < bounds.size(); j++) {
                        ClassOrInterfaceType bound = bounds.get(j);
                        BoundLocation loc = new BoundLocation(i, j);
                        bound.accept(this, method.bounds.getVivify(loc));
                    }
                }
            }
        }
        return body == null ? null : body.accept(this, method);
    }

    @Override
    public Void visit(ObjectCreationExpr expr, AElement elem) {
        ClassOrInterfaceType type = expr.getType();
        AClass clazz = scene.classes.getVivify(type.getNameAsString());
        Expression scope = expr.getScope().orElse(null);
        List<Type> typeArgs = expr.getTypeArguments().orElse(null);
        List<Expression> args = expr.getArguments();
        NodeList<BodyDeclaration<?>> bodyDecls = expr.getAnonymousClassBody().orElse(null);
        if (scope != null) {
            scope.accept(this, elem);
        }
        if (args != null) {
            for (Expression arg : args) {
                arg.accept(this, elem);
            }
        }
        if (typeArgs != null) {
            for (Type typeArg : typeArgs) {
                typeArg.accept(this, elem);
            }
        }
        type.accept(this, clazz);
        if (bodyDecls != null) {
            for (BodyDeclaration<?> decl : bodyDecls) {
                decl.accept(this, clazz);
            }
        }
        return null;
    }

    @Override
    public Void visit(VariableDeclarationExpr expr, AElement elem) {
        List<AnnotationExpr> annos = expr.getAnnotations();
        AMethod method = (AMethod) elem;
        List<VariableDeclarator> varDecls = expr.getVariables();
        for (int i = 0; i < varDecls.size(); i++) {
            VariableDeclarator decl = varDecls.get(i);
            LocalLocation loc = new LocalLocation(i, decl.getNameAsString());
            AField field = method.body.locals.getVivify(loc);
            visitType(expr.getCommonType(), field.type);
            if (annos != null) {
                for (AnnotationExpr annoExpr : annos) {
                    Annotation anno = extractAnnotation(annoExpr);
                    field.tlAnnotationsHere.add(anno);
                }
            }
        }
        return null;
    }

    /**
     * Copies information from an AST declaration node to an {@link ADeclaration}. Called by
     * visitors for BodyDeclaration subclasses.
     */
    private Void visitDecl(BodyDeclaration<?> decl, ADeclaration elem) {
        NodeList<AnnotationExpr> annoExprs = decl.getAnnotations();
        if (annoExprs != null) {
            for (AnnotationExpr annoExpr : annoExprs) {
                Annotation anno = extractAnnotation(annoExpr);
                elem.tlAnnotationsHere.add(anno);
            }
        }
        return null;
    }

    /** Copies information from an AST type node to an {@link ATypeElement}. */
    private Void visitType(Type type, ATypeElement elem) {
        List<AnnotationExpr> exprs = type.getAnnotations();
        if (exprs != null) {
            for (AnnotationExpr expr : exprs) {
                Annotation anno = extractAnnotation(expr);
                if (anno != null) {
                    elem.tlAnnotationsHere.add(anno);
                }
            }
        }
        visitInnerTypes(type, elem);
        return null;
    }

    /**
     * Copies information from an AST type node's inner type nodes to an {@link ATypeElement}.
     *
     * @param type the AST Type node to inspect
     * @param elem destination type element
     */
    private static Void visitInnerTypes(Type type, ATypeElement elem) {
        return type.accept(
                new GenericVisitorAdapter<Void, List<TypePathEntry>>() {
                    @Override
                    public Void visit(ClassOrInterfaceType type, List<TypePathEntry> loc) {
                        if (type.getTypeArguments().isPresent()) {
                            List<Type> typeArgs = type.getTypeArguments().get();
                            for (int i = 0; i < typeArgs.size(); i++) {
                                Type inner = typeArgs.get(i);
                                List<TypePathEntry> ext = extendedTypePath(loc, 3, i);
                                visitInnerType(inner, ext);
                            }
                        }
                        return null;
                    }

                    @Override
                    public Void visit(ArrayType type, List<TypePathEntry> loc) {
                        List<TypePathEntry> ext = loc;
                        int n = type.getArrayLevel();
                        Type currentType = type;
                        for (int i = 0; i < n; i++) {
                            ext = extendedTypePath(ext, 1, 0);
                            for (AnnotationExpr expr : currentType.getAnnotations()) {
                                ATypeElement typeElem = elem.innerTypes.getVivify(ext);
                                Annotation anno = extractAnnotation(expr);
                                typeElem.tlAnnotationsHere.add(anno);
                            }
                            currentType =
                                    ((com.github.javaparser.ast.type.ArrayType) currentType)
                                            .getComponentType();
                        }
                        return null;
                    }

                    @Override
                    public Void visit(WildcardType type, List<TypePathEntry> loc) {
                        ReferenceType lower = type.getExtendedType().orElse(null);
                        ReferenceType upper = type.getSuperType().orElse(null);
                        if (lower != null) {
                            List<TypePathEntry> ext = extendedTypePath(loc, 2, 0);
                            visitInnerType(lower, ext);
                        }
                        if (upper != null) {
                            List<TypePathEntry> ext = extendedTypePath(loc, 2, 0);
                            visitInnerType(upper, ext);
                        }
                        return null;
                    }

                    /**
                     * Copies information from an AST inner type node to an {@link ATypeElement}.
                     */
                    private void visitInnerType(Type type, List<TypePathEntry> loc) {
                        ATypeElement typeElem = elem.innerTypes.getVivify(loc);
                        for (AnnotationExpr expr : type.getAnnotations()) {
                            Annotation anno = extractAnnotation(expr);
                            typeElem.tlAnnotationsHere.add(anno);
                            type.accept(this, loc);
                        }
                    }

                    /**
                     * Extends type path by one element.
                     *
                     * @see TypePathEntry(int, int)
                     */
                    private List<TypePathEntry> extendedTypePath(
                            List<TypePathEntry> loc, int tag, int arg) {
                        List<TypePathEntry> path = new ArrayList<>(loc.size() + 1);
                        path.addAll(loc);
                        path.add(TypePathEntry.create(tag, arg));
                        return path;
                    }
                },
                Collections.emptyList());
    }

    /**
     * Computes a type's "binary name".
     *
     * @param type the type
     * @return the type's binary name
     */
    private String getJVML(Type type) {
        return type.accept(
                new GenericVisitorAdapter<String, Void>() {
                    @Override
                    public String visit(ClassOrInterfaceType type, Void v) {
                        @SuppressWarnings(
                                "signature") // https://tinyurl.com/cfissue/658 for getNameAsString
                        @FullyQualifiedName String typeName = type.getNameAsString();
                        @SuppressWarnings("signature" // TODO:  bug in ToIndexFileConverter:
                        // resolve requires a @BinaryName, but this passes a @FullyQualifiedName.
                        // They differ for inner classes.
                        )
                        String name = resolve(typeName);
                        if (name == null) {
                            // could be defined in the same stub file
                            return "L" + typeName + ";";
                        }
                        return "L" + String.join("/", name.split("\\.")) + ";";
                    }

                    @Override
                    public String visit(PrimitiveType type, Void v) {
                        switch (type.getType()) {
                            case BOOLEAN:
                                return "Z";
                            case BYTE:
                                return "B";
                            case CHAR:
                                return "C";
                            case DOUBLE:
                                return "D";
                            case FLOAT:
                                return "F";
                            case INT:
                                return "I";
                            case LONG:
                                return "J";
                            case SHORT:
                                return "S";
                            default:
                                throw new BugInCF("unknown primitive type " + type);
                        }
                    }

                    @Override
                    public String visit(ArrayType type, Void v) {
                        String typeName = type.getElementType().accept(this, null);
                        StringBuilder sb = new StringBuilder();
                        int n = type.getArrayLevel();
                        for (int i = 0; i < n; i++) {
                            sb.append("[");
                        }
                        sb.append(typeName);
                        return sb.toString();
                    }

                    @Override
                    public String visit(VoidType type, Void v) {
                        return "V";
                    }

                    @Override
                    public String visit(WildcardType type, Void v) {
                        return type.getSuperType().get().accept(this, null);
                    }
                },
                null);
    }

    /**
     * Finds the fully qualified name of the class with the given name.
     *
     * @param className possibly unqualified name of class
     * @return fully qualified name of class that {@code className} identifies in the current
     *     context, or null if resolution fails
     */
    private @Nullable @BinaryName String resolve(@BinaryName String className) {

        if (pkgName != null) {
            String qualifiedName = Signatures.addPackage(pkgName, className);
            if (loadClass(qualifiedName) != null) {
                return qualifiedName;
            }
        }

        {
            // Every Java program implicitly does "import java.lang.*",
            // so see whether this class is in that package.
            String qualifiedName = Signatures.addPackage("java.lang", className);
            if (loadClass(qualifiedName) != null) {
                return qualifiedName;
            }
        }

        for (String declName : imports) {
            String qualifiedName = mergeImport(declName, className);
            if (loadClass(qualifiedName) != null) {
                return qualifiedName;
            }
        }

        if (loadClass(className) != null) {
            return className;
        }

        return null;
    }

    /**
     * Combines an import with a partial binary name, yielding a binary name.
     *
     * @param importName package name or (for an inner class) the outer class name
     * @param className the class name
     * @return fully qualified class name if resolution succeeds, null otherwise
     */
    @SuppressWarnings("signature") // string manipulation of signature strings
    private static @Nullable @BinaryName String mergeImport(
            String importName, @BinaryName String className) {
        if (importName.isEmpty() || importName.equals(className)) {
            return className;
        }
        String[] importSplit = importName.split("\\.");
        String[] classSplit = className.split("\\.");
        String importEnd = importSplit[importSplit.length - 1];
        if ("*".equals(importEnd)) {
            return importName.substring(0, importName.length() - 1) + className;
        } else {
            // find overlap such as in
            //   import a.b.C.D;
            //   C.D myvar;
            int i = importSplit.length;
            int n = i - classSplit.length;
            while (--i >= n) {
                if (!classSplit[i - n].equals(importSplit[i])) {
                    return null;
                }
            }
            return importName;
        }
    }

    /**
     * Finds the {@link Class} corresponding to a name.
     *
     * @param className a class name
     * @return the {@link Class} object corresponding to {@code className}, or null if none found
     */
    private static @Nullable Class<?> loadClass(@ClassGetName String className) {
        assert className != null;
        try {
            return Class.forName(className, false, null);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
