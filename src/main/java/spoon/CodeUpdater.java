package spoon;

import com.google.gson.*;
import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CodeUpdater {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        // 1. Load GPT-generated JSON configuration
        JsonObject config = loadJsonConfig("src/main/java/spoon/gpt-suggestions.json");

        // 2. Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource("spoon.NumberFormatException");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.buildModel();

        // 3. Process each class modification
        JsonArray classes = config.getAsJsonArray("classes");
        for (JsonElement classElement : classes) {
            processClassModification(launcher.getFactory(), classElement.getAsJsonObject());
        }

        // 4. Apply global changes
        if (config.has("global_changes")) {
            processGlobalChanges(launcher.getFactory(), config.getAsJsonArray("global_changes"));
        }

        // 5. Output modified code
        launcher.setSourceOutputDirectory("target/spooned/");
        launcher.prettyprint();
        System.out.println("Code updates completed successfully!");
    }

    private static JsonObject loadJsonConfig(String filePath) throws Exception {
        String content = Files.readString(Path.of(filePath));
        return JsonParser.parseString(content).getAsJsonObject();
    }

    private static void processClassModification(Factory factory, JsonObject classConfig) {
        String className = classConfig.get("name").getAsString();
        CtType<?> ctClass = factory.Type().get(className);

        if (ctClass == null) {
            System.err.println("Class not found: " + className);
            return;
        }

        // Handle inheritance
        if (classConfig.has("extends")) {
            String superClass = classConfig.get("extends").getAsString();
            ((CtClass<?>) ctClass).setSuperclass(factory.Type().createReference(superClass));
        }

        // Process methods
        if (classConfig.has("methods")) {
            JsonArray methods = classConfig.getAsJsonArray("methods");
            for (JsonElement methodElement : methods) {
                processMethodModification(factory, (CtClass<?>) ctClass, methodElement.getAsJsonObject());
            }
        }

        // Process fields
        if (classConfig.has("fields")) {
            JsonArray fields = classConfig.getAsJsonArray("fields");
            for (JsonElement fieldElement : fields) {
                processFieldModification(factory, (CtClass<?>) ctClass, fieldElement.getAsJsonObject());
            }
        }
    }

    private static void processMethodModification(Factory factory, CtClass<?> ctClass, JsonObject methodConfig) {
        String action = methodConfig.get("action").getAsString();
        String methodName = methodConfig.get("name").getAsString();

        switch (action) {
            case "add":
                CtMethod<?> newMethod = factory.createMethod();
                newMethod.setSimpleName(methodName);

                // Set parameters
                if (methodConfig.has("params")) {
                    JsonArray params = methodConfig.getAsJsonArray("params");
                    for (JsonElement paramElement : params) {
                        JsonObject param = paramElement.getAsJsonObject();
                        CtParameter<?> ctParam = factory.createParameter();
                        ctParam.setType(factory.Type().createReference(param.get("type").getAsString()));
                        ctParam.setSimpleName(param.get("name").getAsString());
                        newMethod.addParameter(ctParam);
                    }
                }

                // Set return type
                String returnType = methodConfig.has("return_type") ?
                        methodConfig.get("return_type").getAsString() : "void";
                newMethod.setType(factory.Type().createReference(returnType));

                // Set body
                newMethod.setBody(factory.createCodeSnippetStatement(methodConfig.get("body").getAsString()));

                // Add modifiers
                if (methodConfig.has("modifiers")) {
                    JsonArray modifiers = methodConfig.getAsJsonArray("modifiers");
                    modifiers.forEach(m -> newMethod.addModifier(ModifierKind.valueOf(m.getAsString())));
                }

                ctClass.addMethod(newMethod);
                break;

            case "modify":
                CtMethod<?> existingMethod = ctClass.getMethod(methodName);
                if (existingMethod != null && methodConfig.has("changes")) {
                    processMethodChanges(factory, existingMethod, methodConfig.getAsJsonArray("changes"));
                }
                break;
        }
    }

    private static void processMethodChanges(Factory factory, CtMethod<?> method, JsonArray changes) {
        for (JsonElement changeElement : changes) {
            JsonObject change = changeElement.getAsJsonObject();
            switch (change.get("type").getAsString()) {
                case "add_parameter":
                    JsonObject param = change.getAsJsonObject("param");
                    CtParameter<?> ctParam = factory.createParameter();
                    ctParam.setType(factory.Type().createReference(param.get("type").getAsString()));
                    ctParam.setSimpleName(param.get("name").getAsString());
                    method.addParameter(ctParam);
                    break;

                case "insert_code":
                    String currentBody = method.getBody().toString();
                    String newCode = change.get("code").getAsString();
                    if ("beginning".equals(change.get("location").getAsString())) {
                        method.setBody(factory.createCodeSnippetStatement(newCode + "\n" + currentBody));
                    } else {
                        method.setBody(factory.createCodeSnippetStatement(currentBody + "\n" + newCode));
                    }
                    break;
            }
        }
    }

    private static void processFieldModification(Factory factory, CtClass<?> ctClass, JsonObject fieldConfig) {
        String action = fieldConfig.get("action").getAsString();
        if ("add".equals(action)) {
            CtField<?> newField = factory.createField();
            newField.setType(factory.Type().createReference(fieldConfig.get("type").getAsString()));
            newField.setSimpleName(fieldConfig.get("name").getAsString());

            if (fieldConfig.has("value")) {
                newField.setDefaultExpression(factory.createCodeSnippetExpression(fieldConfig.get("value").getAsString()));
            }

            if (fieldConfig.has("modifiers")) {
                JsonArray modifiers = fieldConfig.getAsJsonArray("modifiers");
                modifiers.forEach(m -> newField.addModifier(ModifierKind.valueOf(m.getAsString())));
            }

            ctClass.addField(newField);
        }
    }

    private static void processGlobalChanges(Factory factory, JsonArray globalChanges) {
        for (JsonElement changeElement : globalChanges) {
            JsonObject change = changeElement.getAsJsonObject();
            switch (change.get("type").getAsString()) {
                case "import":
                    String importClass = change.get("class").getAsString();
//                    factory.getEnvironment().getAutoImports().addImport(importClass);
                    break;

                case "log_add":
                    // Implementation for adding logging framework would go here
                    break;
            }
        }
    }
}
