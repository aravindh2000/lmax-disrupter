package spoon;

import org.eclipse.jdt.core.dom.*;

public class ASTProcessor {
    public static void main(String[] args) {
        // The source code to analyze
        String sourceCode = "" +
                " public void test_call()\n" +
                "    {\n" +
                "        // This string cannot be parsed as a number\n" +
                "        String invalidNumber = \"abc123\";\n" +
                "\n" +
                "        // Attempt to parse the non-numeric string\n" +
                "        int number = Integer.parseInt(invalidNumber);\n" +
                "\n" +
                "        System.out.println(\"Parsed number: \" + number);\n" +
                "    }";

        // Create AST parser
        ASTParser parser = ASTParser.newParser(AST.JLS15);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // Parse and get the AST
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        // Create visitor to analyze the AST
        cu.accept(new ASTVisitor() {
            // Track variable declarations
            int variableCount = 0;
            int methodCallCount = 0;

            @Override
            public boolean visit(TypeDeclaration node) {
                System.out.println("Class Declaration: " + node.getName());
                return true; // continue visiting child nodes
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                System.out.println("\nMethod Declaration: " + node.getName());
                System.out.println("Parameters: " + node.parameters());
                return true;
            }

            @Override
            public boolean visit(VariableDeclarationStatement node) {
                variableCount++;
                System.out.println("\nVariable Declaration #" + variableCount + ":");
                for (Object fragment : node.fragments()) {
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                    System.out.println("  Name: " + vdf.getName());
                    System.out.println("  Type: " + node.getType());
                    if (vdf.getInitializer() != null) {
                        System.out.println("  Initializer: " + vdf.getInitializer());
                    }
                }
                return true;
            }

            @Override
            public boolean visit(MethodInvocation node) {
                methodCallCount++;
                System.out.println("\nMethod Call #" + methodCallCount + ":");
                System.out.println("  Name: " + node.getName());
                System.out.println("  Expression: " + node.getExpression());
                System.out.println("  Arguments: " + node.arguments());
                return true;
            }

            @Override
            public boolean visit(InfixExpression node) {
                System.out.println("\nBinary Operation:");
                System.out.println("  Left Operand: " + node.getLeftOperand());
                System.out.println("  Operator: " + node.getOperator());
                System.out.println("  Right Operand: " + node.getRightOperand());
                return true;
            }

            @Override
            public void endVisit(CompilationUnit node) {
                System.out.println("\nSummary:");
                System.out.println("  Total variables declared: " + variableCount);
                System.out.println("  Total method calls: " + methodCallCount);
            }
        });
    }
}
