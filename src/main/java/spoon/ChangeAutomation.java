package spoon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.TypeFactory;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChangeAutomation {


    public static void main(String[] args) throws Exception {
        // Read JSON configuration
        String jsonConfig = new String(Files.readAllBytes(Path.of("src/main/java/spoon/config.json")));
        JsonObject config = JsonParser.parseString(jsonConfig).getAsJsonObject();

        // Extract values from JSON using keys
        String targetClass = "spoon.NumberFormatException";
        JsonObject methodConfig = config.getAsJsonObject("new_method");
        String methodName = methodConfig.get("name").getAsString();
        String methodBody = methodConfig.get("body").getAsString();

        // Configure Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource("src/main/java/spoon/NumberformatException.java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        // Build the model
        launcher.buildModel();
        Factory factory = launcher.getFactory();

        // Get the target class
        CtClass<?> ctClass = (CtClass<?>) factory.Type().get(targetClass);

        // Create the new method
        CtMethod<?> newMethod = factory.createMethod();
        newMethod.setSimpleName(methodName);




        /// Add parameters from JSON with proper type conversion
        methodConfig.getAsJsonArray("params").forEach(parameter -> {
            JsonObject paramObj = parameter.getAsJsonObject();
            String paramTypeStr = paramObj.get("type").getAsString();
            String paramName = paramObj.has("name") ?
                    paramObj.get("name").getAsString() :
                    "parameter" + newMethod.getParameters().size();

            // Convert string type to Class object
            CtTypeReference paramClass;

            paramClass = (CtTypeReference) getPrimitiveClass(paramTypeStr); // Handle primitives first


            // Create parameter with proper type reference
            CtParameter<String> pm = factory.createParameter();
            pm.setType(factory.Type().STRING); // Parameter type (e.g., String)
            pm.setSimpleName(paramName);     // Parameter name
            newMethod.addParameter(pm);
        });

        // Set return type if specified
        if (methodConfig.has("return_type")) {
            String returnType = methodConfig.get("return_type").getAsString();
            newMethod.setType(factory.Type().createReference(returnType));
        }

        // Set method body
        newMethod.setBody(factory.createCodeSnippetStatement(methodBody));

        // Add method to class
        ctClass.addMethod(newMethod);

        // Process method call if specified
        if (config.has("call_in_method")) {
            JsonObject callConfig = config.getAsJsonObject("call_in_method");
            String targetMethod = callConfig.get("target_method").getAsString();
            String callCode = callConfig.get("call").getAsString();

            CtMethod<?> existingMethod = ctClass.getMethod(targetMethod);
            String modifiedBody = existingMethod.getBody().toString() + "\n" + callCode;
            existingMethod.setBody(factory.createCodeSnippetStatement(modifiedBody));
        }

        // Output the modified code
        launcher.setSourceOutputDirectory("target/generated-sources/");
        launcher.prettyprint();

        System.out.println("Code modification completed successfully!");
    }

    // Helper method to handle primitive types
    private static TypeFactory getPrimitiveClass(String typeName) {
        switch (typeName) {
            case "String" : return (TypeFactory) new TypeFactory().STRING;
            default: return null;
        }
    }
}
