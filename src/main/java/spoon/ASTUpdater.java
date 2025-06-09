package spoon;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.*;
import java.util.*;

public class ASTUpdater {
    private static AST ast;


    public static void main(String[] args) {
        String baseCode = "public class Calculator {\n" +
                "    private int total;\n" +
                "    public int add(int a, int b) {\n" +
                "        return a + b;\n" +
                "    }\n" +
                "}";

        String llmCode = "import java.util.*;\n" +
                "public final class Calculator extends Objects{\n" +
                "    private int total;\n" +
                "    public int add(int a, int b,int c) {\n" +
                "        return a + b;\n" +
                "    }\n" +
                "    private List<Operation> history;\n" +
                "    public double add(double a, double b) {\n" +
                "        return a + b;\n" +
                "    }\n" +
                "    public void clear() {\n" +
                "        total = 0;\n" +
                "    }\n" +
                "}";

        String updatedCode = applyLLMChanges(baseCode, llmCode);
        System.out.println(updatedCode);
    }

    public static String applyLLMChanges(String baseCode, String llmCode) {
        // Parse both code versions
        CompilationUnit baseAST = parseAST(baseCode);
        CompilationUnit llmAST = parseAST(llmCode);

        // Initialize the AST reference
        ast = baseAST.getAST();

        // Create rewriter for the base code
        ASTRewrite rewriter = ASTRewrite.create(ast);

        // Process all code elements
        updateImports(baseAST, llmAST, rewriter);
        updateTypeDeclarations(baseAST, llmAST, rewriter);

        // Apply changes
        try {
            Document document = new Document(baseCode);
            TextEdit edits = rewriter.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply changes", e);
        }
    }

    private static void updateImports(CompilationUnit baseAST, CompilationUnit llmAST, ASTRewrite rewriter) {
        Set<String> existingImports = new HashSet<>();
        for (Object imp : baseAST.imports()) {
            existingImports.add(((ImportDeclaration) imp).getName().getFullyQualifiedName());
        }

        ListRewrite importRewriter = rewriter.getListRewrite(baseAST, CompilationUnit.IMPORTS_PROPERTY);
        for (Object imp : llmAST.imports()) {
            ImportDeclaration llmImport = (ImportDeclaration) imp;
            String importName = llmImport.getName().getFullyQualifiedName();

            if (!existingImports.contains(importName)) {
                ImportDeclaration newImport = ast.newImportDeclaration();
                newImport.setName(ast.newName(importName.split("\\.")));
                newImport.setStatic(llmImport.isStatic());
                importRewriter.insertLast(newImport, null);
            }
        }
    }

    private static void updateTypeDeclarations(CompilationUnit baseAST, CompilationUnit llmAST, ASTRewrite rewriter) {
        TypeDeclaration baseType = (TypeDeclaration) baseAST.types().get(0);
        TypeDeclaration llmType = (TypeDeclaration) llmAST.types().get(0);

        // Update type modifiers using standard API
        updateModifiers(baseType, llmType, rewriter);

        // Update type documentation (Javadoc)
        if (llmType.getJavadoc() != null) {
            Javadoc newDoc = (Javadoc) ASTNode.copySubtree(ast, llmType.getJavadoc());
            rewriter.set(baseType, TypeDeclaration.JAVADOC_PROPERTY, newDoc, null);
        }

        // Update all members
        updateFields(baseType, llmType, rewriter);
        updateMethods(baseType, llmType, rewriter);
        updateInnerTypes(baseType, llmType, rewriter);
    }

    private static void updateInnerTypes(TypeDeclaration baseType, TypeDeclaration llmType, ASTRewrite rewriter) {
        Set<String> existingInnerTypes = new HashSet<>();
        for (AbstractTypeDeclaration type : baseType.getTypes()) {
            existingInnerTypes.add(type.getName().getIdentifier());
        }

        ListRewrite typeRewriter = rewriter.getListRewrite(baseType, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (AbstractTypeDeclaration llmInnerType : llmType.getTypes()) {
            if (!existingInnerTypes.contains(llmInnerType.getName().getIdentifier())) {
                AbstractTypeDeclaration newType = (AbstractTypeDeclaration) ASTNode.copySubtree(ast, llmInnerType);
                typeRewriter.insertLast(newType, null);
            }
        }
    }

    private static void updateModifiers(TypeDeclaration baseType, TypeDeclaration llmType, ASTRewrite rewriter) {
        // Get the property name for modifiers list
        StructuralPropertyDescriptor modifiersProperty =
                baseType.getAST().apiLevel() >= AST.JLS8
                        ? TypeDeclaration.MODIFIERS2_PROPERTY
                        : TypeDeclaration.MODIFIERS_PROPERTY;

        assert modifiersProperty instanceof ChildListPropertyDescriptor;
        ListRewrite modifierRewriter = rewriter.getListRewrite((ASTNode) baseType, (ChildListPropertyDescriptor) modifiersProperty);

        // First remove all existing modifiers
        @SuppressWarnings("unchecked")
        List<IExtendedModifier> modifiers = baseType.modifiers();
        for (IExtendedModifier mod : modifiers) {
            modifierRewriter.remove((ASTNode) mod, null);
        }

        // Add all modifiers from LLM version
        for (Object mod : llmType.modifiers()) {
            modifierRewriter.insertLast(ASTNode.copySubtree(ast, (ASTNode) mod), null);
        }
    }

    private static void updateFields(TypeDeclaration baseType, TypeDeclaration llmType, ASTRewrite rewriter) {
        Set<String> existingFields = new HashSet<>();
        for (FieldDeclaration field : baseType.getFields()) {
            for (Object frag : field.fragments()) {
                existingFields.add(((VariableDeclarationFragment) frag).getName().getIdentifier());
            }
        }

        ListRewrite fieldRewriter = rewriter.getListRewrite(baseType,
                TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

        for (FieldDeclaration llmField : llmType.getFields()) {
            boolean isNewField = false;
            for (Object frag : llmField.fragments()) {
                String fieldName = ((VariableDeclarationFragment) frag).getName().getIdentifier();
                if (!existingFields.contains(fieldName)) {
                    isNewField = true;
                    break;
                }
            }

            if (isNewField) {
                FieldDeclaration newField = (FieldDeclaration) ASTNode.copySubtree(ast, llmField);
                fieldRewriter.insertLast(newField, null);
            }
        }
    }

    private static void updateMethods(TypeDeclaration baseType, TypeDeclaration llmType, ASTRewrite rewriter) {
        Map<String, MethodDeclaration> existingMethods = new HashMap<>();
        for (MethodDeclaration method : baseType.getMethods()) {
            existingMethods.put(getMethodSignature(method), method);
        }

        ListRewrite methodRewriter = rewriter.getListRewrite(baseType,
                TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

        for (MethodDeclaration llmMethod : llmType.getMethods()) {
            String methodSig = getMethodSignature(llmMethod);
            if (!existingMethods.containsKey(methodSig)) {
                // Add new method
                MethodDeclaration newMethod = (MethodDeclaration) ASTNode.copySubtree(ast, llmMethod);
                methodRewriter.insertLast(newMethod, null);
            } else {
                // Update existing method
                MethodDeclaration existingMethod = existingMethods.get(methodSig);

                // Update return type if changed
                if (!llmMethod.getReturnType2().toString().equals(existingMethod.getReturnType2().toString())) {
                    Type newType = (Type) ASTNode.copySubtree(ast, llmMethod.getReturnType2());
                    rewriter.set(existingMethod, MethodDeclaration.RETURN_TYPE2_PROPERTY, newType, null);
                }

                // Update method body
                if (llmMethod.getBody() != null) {
                    Block newBody = (Block) ASTNode.copySubtree(ast, llmMethod.getBody());
                    rewriter.set(existingMethod, MethodDeclaration.BODY_PROPERTY, newBody, null);
                }

                // Update parameters if changed
                if (llmMethod.parameters().size() == existingMethod.parameters().size()) {
                    ListRewrite paramRewriter = rewriter.getListRewrite(existingMethod,
                            MethodDeclaration.PARAMETERS_PROPERTY);

                    for (int i = 0; i < llmMethod.parameters().size(); i++) {
                        SingleVariableDeclaration newParam = (SingleVariableDeclaration)
                                ASTNode.copySubtree(ast, (ASTNode) llmMethod.parameters().get(i));
                        paramRewriter.replace((ASTNode) existingMethod.parameters().get(i), newParam, null);
                    }
                }
            }
        }
    }

    private static String getMethodSignature(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder(method.getName().getIdentifier());
        sb.append("(");
        for (Object param : method.parameters()) {
            SingleVariableDeclaration p = (SingleVariableDeclaration) param;
            sb.append(p.getType().toString()).append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    private static CompilationUnit parseAST(String code) {
        ASTParser parser = ASTParser.newParser(AST.JLS15);
        parser.setSource(code.toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }
}